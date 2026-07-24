package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.internal.dto.AiStatsResponse;
import com.pravoos.ai.core.internal.dto.WorkflowStat;
import com.pravoos.ai.core.internal.repository.jpa.AiResponseRepository;
import com.pravoos.ai.shared.model.enums.BankruptcyWorkflow;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatsService {

  private final AiResponseRepository aiResponseRepository;
  private final MeterRegistry meterRegistry;

  public AdminStatsService(AiResponseRepository aiResponseRepository, MeterRegistry meterRegistry) {
    this.aiResponseRepository = aiResponseRepository;
    this.meterRegistry = meterRegistry;
  }

  @Transactional(readOnly = true)
  public AiStatsResponse getStats() {
    long total = aiResponseRepository.count();
    long rated = aiResponseRepository.countRated();
    long positive = aiResponseRepository.countPositive();
    long negative = aiResponseRepository.countNegative();

    long guardPassed = counter("pravoos.guard", "result", "pass");
    long guardRefusals = counter("pravoos.guard", "result", "block");
    long citationsVerified = counter("pravoos.citation.checks", "status", "verified");
    long citationsChecked =
        citationsVerified
            + counter("pravoos.citation.checks", "status", "not_found")
            + counter("pravoos.citation.checks", "status", "unverified");

    List<WorkflowStat> workflows =
        aiResponseRepository.aggregateByWorkflow().stream().map(this::toWorkflowStat).toList();

    return new AiStatsResponse(
        total,
        rated,
        positive,
        negative,
        guardPassed + guardRefusals,
        guardRefusals,
        citationsChecked,
        citationsVerified,
        workflows);
  }

  private long counter(String name, String tagKey, String tagValue) {
    var counter = Search.in(meterRegistry).name(name).tag(tagKey, tagValue).counter();
    return counter == null ? 0L : (long) counter.count();
  }

  @Transactional(readOnly = true)
  public List<AiResponseDto> getRecentResponses() {
    return aiResponseRepository.findTop50ByOrderByCreatedAtDesc().stream()
        .map(AiResponseDto::from)
        .toList();
  }

  private WorkflowStat toWorkflowStat(Object[] row) {
    String workflowId = (String) row[0];
    long count = ((Number) row[1]).longValue();
    Double avgRating = row[2] != null ? ((Number) row[2]).doubleValue() : null;
    return new WorkflowStat(workflowId, resolveName(workflowId), count, avgRating);
  }

  private String resolveName(String workflowId) {
    try {
      return BankruptcyWorkflow.valueOf(workflowId).displayName();
    } catch (IllegalArgumentException ex) {
      return workflowId;
    }
  }
}
