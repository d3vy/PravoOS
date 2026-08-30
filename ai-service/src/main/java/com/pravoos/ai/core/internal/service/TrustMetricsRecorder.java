package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.internal.repository.jpa.AiTrustCounterRepository;
import com.pravoos.ai.shared.model.enums.TrustMetric;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrustMetricsRecorder {

  private static final Logger log = LoggerFactory.getLogger(TrustMetricsRecorder.class);

  private record PendingFlush(UUID attemptId, long delta) {}

  private final AiTrustCounterRepository trustCounterRepository;
  private final TrustCounterWriter trustCounterWriter;
  private final Map<TrustMetric, LongAdder> pending = new ConcurrentHashMap<>();
  private final Map<TrustMetric, PendingFlush> retryQueue = new ConcurrentHashMap<>();

  public TrustMetricsRecorder(
      AiTrustCounterRepository trustCounterRepository, TrustCounterWriter trustCounterWriter) {
    this.trustCounterRepository = trustCounterRepository;
    this.trustCounterWriter = trustCounterWriter;
  }

  public void record(TrustMetric metric) {
    pending.computeIfAbsent(metric, key -> new LongAdder()).increment();
  }

  @Scheduled(fixedDelayString = "${trust-metrics.flush-interval-ms:60000}")
  public void flush() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    for (TrustMetric metric : TrustMetric.values()) {
      PendingFlush stuck = retryQueue.get(metric);
      if (stuck != null) {
        if (tryWrite(today, metric, stuck.attemptId(), stuck.delta())) {
          retryQueue.remove(metric);
        } else {
          continue;
        }
      }
      LongAdder adder = pending.get(metric);
      if (adder == null) {
        continue;
      }
      long delta = adder.sumThenReset();
      if (delta == 0) {
        continue;
      }
      UUID attemptId = UUID.randomUUID();
      if (!tryWrite(today, metric, attemptId, delta)) {
        retryQueue.put(metric, new PendingFlush(attemptId, delta));
      }
    }
  }

  private boolean tryWrite(LocalDate statDate, TrustMetric metric, UUID attemptId, long delta) {
    try {
      trustCounterWriter.addDelta(attemptId, statDate, metric, delta);
      return true;
    } catch (RuntimeException ex) {
      log.warn("Не удалось записать метрику доверия {}: {}", metric, ex.getMessage());
      return false;
    }
  }

  @PreDestroy
  void flushOnShutdown() {
    flush();
  }

  @Transactional(readOnly = true)
  public Map<TrustMetric, Long> totals() {
    Map<TrustMetric, Long> totals = new EnumMap<>(TrustMetric.class);
    for (TrustMetric metric : TrustMetric.values()) {
      totals.put(metric, 0L);
    }
    for (Object[] row : trustCounterRepository.aggregateByMetric()) {
      totals.merge(TrustMetric.valueOf((String) row[0]), ((Number) row[1]).longValue(), Long::sum);
    }
    pending.forEach((metric, adder) -> totals.merge(metric, adder.sum(), Long::sum));
    retryQueue.forEach((metric, retry) -> totals.merge(metric, retry.delta(), Long::sum));
    return totals;
  }
}
