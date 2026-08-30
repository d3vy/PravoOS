package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.internal.repository.jpa.AiTrustCounterRepository;
import com.pravoos.ai.shared.model.enums.TrustMetric;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrustCounterWriter {

  private final AiTrustCounterRepository trustCounterRepository;

  public TrustCounterWriter(AiTrustCounterRepository trustCounterRepository) {
    this.trustCounterRepository = trustCounterRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void addDelta(UUID attemptId, LocalDate statDate, TrustMetric metric, long delta) {
    trustCounterRepository.addDelta(attemptId, statDate, metric.name(), delta);
  }
}
