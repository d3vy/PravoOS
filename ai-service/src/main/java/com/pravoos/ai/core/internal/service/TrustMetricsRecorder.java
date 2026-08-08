package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.internal.repository.jpa.AiTrustCounterRepository;
import com.pravoos.ai.shared.model.enums.TrustMetric;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
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

  private final AiTrustCounterRepository trustCounterRepository;
  private final TrustCounterWriter trustCounterWriter;
  private final Map<TrustMetric, LongAdder> pending = new ConcurrentHashMap<>();

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
    for (Map.Entry<TrustMetric, LongAdder> entry : pending.entrySet()) {
      long delta = entry.getValue().sumThenReset();
      if (delta == 0) {
        continue;
      }
      try {
        trustCounterWriter.addDelta(today, entry.getKey(), delta);
      } catch (RuntimeException ex) {
        entry.getValue().add(delta);
        log.warn("Не удалось записать метрику доверия {}: {}", entry.getKey(), ex.getMessage());
      }
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
    return totals;
  }
}
