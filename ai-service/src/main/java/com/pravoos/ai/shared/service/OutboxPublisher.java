package com.pravoos.ai.shared.service;

import com.pravoos.ai.shared.model.entity.OutboxEvent;
import com.pravoos.ai.shared.repository.jpa.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;
    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, String> stringKafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.stringKafkaTemplate = stringKafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository
                .lockUnpublishedBatch(PageRequest.of(0, BATCH_SIZE));
        if (pending.isEmpty()) {
            return;
        }
        Map<OutboxEvent, CompletableFuture<?>> inFlight = new LinkedHashMap<>();
        for (OutboxEvent event : pending) {
            inFlight.put(event,
                    stringKafkaTemplate.send(event.getTopic(), event.getKafkaKey(), event.getPayload()));
        }
        inFlight.forEach(this::awaitResult);
    }

    private void awaitResult(OutboxEvent event, CompletableFuture<?> sendResult) {
        try {
            sendResult.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            event.markPublished();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            event.incrementAttempts();
            log.error("Interrupted while publishing outbox event {} to topic {}", event.getId(), event.getTopic(), ex);
        } catch (Exception ex) {
            event.incrementAttempts();
            log.error("Failed to publish outbox event {} to topic {} (attempt {})",
                    event.getId(), event.getTopic(), event.getAttempts(), ex);
        }
    }
}
