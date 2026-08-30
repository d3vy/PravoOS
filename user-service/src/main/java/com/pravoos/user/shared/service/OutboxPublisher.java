package com.pravoos.user.shared.service;

import com.pravoos.user.shared.model.entity.OutboxEvent;
import com.pravoos.user.shared.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private static final int BATCH_SIZE = 50;
  private static final long SEND_TIMEOUT_SECONDS = 5L;

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> stringKafkaTemplate;
  private final int maxAttempts;
  private final AtomicLong parkedEvents = new AtomicLong();

  public OutboxPublisher(
      OutboxEventRepository outboxEventRepository,
      KafkaTemplate<String, String> stringKafkaTemplate,
      MeterRegistry meterRegistry,
      @Value("${app.outbox.max-attempts:10}") int maxAttempts) {
    this.outboxEventRepository = outboxEventRepository;
    this.stringKafkaTemplate = stringKafkaTemplate;
    this.maxAttempts = maxAttempts;
    Gauge.builder("pravoos.outbox.parked", parkedEvents, AtomicLong::doubleValue)
        .description("Outbox events that exhausted their retries and will never be published")
        .register(meterRegistry);
  }

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
  @SchedulerLock(
      name = "OutboxPublisher_publishPending",
      lockAtMostFor = "PT1M",
      lockAtLeastFor = "PT1S")
  @Transactional
  public void publishPending() {
    parkedEvents.set(outboxEventRepository.countParked(maxAttempts));
    List<OutboxEvent> pending =
        outboxEventRepository.lockUnpublishedBatch(maxAttempts, PageRequest.of(0, BATCH_SIZE));
    if (pending.isEmpty()) {
      return;
    }
    Map<OutboxEvent, CompletableFuture<?>> inFlight = new LinkedHashMap<>();
    for (OutboxEvent event : pending) {
      inFlight.put(
          event,
          stringKafkaTemplate.send(event.getTopic(), event.getKafkaKey(), event.getPayload()));
    }
    long batchDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(SEND_TIMEOUT_SECONDS);
    inFlight.forEach((event, sendResult) -> awaitResult(event, sendResult, batchDeadlineNanos));
  }

  private void awaitResult(
      OutboxEvent event, CompletableFuture<?> sendResult, long batchDeadlineNanos) {
    try {
      sendResult.get(Math.max(0L, batchDeadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
      event.markPublished();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      event.incrementAttempts();
      log.error(
          "Interrupted while publishing outbox event {} to topic {}",
          event.getId(),
          event.getTopic(),
          ex);
    } catch (Exception ex) {
      event.incrementAttempts();
      log.error(
          "Failed to publish outbox event {} to topic {} (attempt {})",
          event.getId(),
          event.getTopic(),
          event.getAttempts(),
          ex);
      if (event.getAttempts() >= maxAttempts) {
        log.error(
            "Outbox event {} to topic {} parked after {} attempts and will no longer be"
                + " retried automatically",
            event.getId(),
            event.getTopic(),
            event.getAttempts());
      }
    }
  }
}
