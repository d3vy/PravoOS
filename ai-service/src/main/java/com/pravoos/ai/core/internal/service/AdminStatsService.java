package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.internal.dto.AgentToolStat;
import com.pravoos.ai.core.internal.dto.AiStatsResponse;
import com.pravoos.ai.core.internal.dto.WorkflowStat;
import com.pravoos.ai.core.internal.model.entity.AiActionProposalStatus;
import com.pravoos.ai.core.internal.repository.jpa.AiActionProposalRepository;
import com.pravoos.ai.core.internal.repository.jpa.AiResponseRepository;
import com.pravoos.ai.shared.model.enums.BankruptcyWorkflow;
import com.pravoos.ai.shared.model.enums.TrustMetric;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatsService {

  private final AiResponseRepository aiResponseRepository;
  private final TrustMetricsRecorder trustMetricsRecorder;
  private final AiActionProposalRepository proposalRepository;

  public AdminStatsService(
      AiResponseRepository aiResponseRepository,
      TrustMetricsRecorder trustMetricsRecorder,
      AiActionProposalRepository proposalRepository) {
    this.aiResponseRepository = aiResponseRepository;
    this.trustMetricsRecorder = trustMetricsRecorder;
    this.proposalRepository = proposalRepository;
  }

  @Transactional(readOnly = true)
  public AiStatsResponse getStats() {
    long total = aiResponseRepository.count();
    long rated = aiResponseRepository.countRated();
    long positive = aiResponseRepository.countPositive();
    long negative = aiResponseRepository.countNegative();

    Map<TrustMetric, Long> trust = trustMetricsRecorder.totals();
    long guardPassed = trust.get(TrustMetric.GUARD_PASS);
    long guardRefusals = trust.get(TrustMetric.GUARD_BLOCK);
    long citationsVerified = trust.get(TrustMetric.CITATION_VERIFIED);
    long citationsChecked =
        trust.entrySet().stream()
            .filter(entry -> entry.getKey().isCitation())
            .mapToLong(Map.Entry::getValue)
            .sum();

    List<WorkflowStat> workflows =
        aiResponseRepository.aggregateByWorkflow().stream().map(this::toWorkflowStat).toList();

    List<AgentToolStat> agentTools = aggregateAgentTools();

    return new AiStatsResponse(
        total,
        rated,
        positive,
        negative,
        guardPassed + guardRefusals,
        guardRefusals,
        citationsChecked,
        citationsVerified,
        workflows,
        agentTools);
  }

  private List<AgentToolStat> aggregateAgentTools() {
    // index: 0=created(all statuses), 1=approved, 2=rejected, 3=expired, 4=failed
    Map<String, long[]> byTool = new LinkedHashMap<>();
    for (Object[] row : proposalRepository.aggregateByToolAndStatus()) {
      String toolName = (String) row[0];
      AiActionProposalStatus status = (AiActionProposalStatus) row[1];
      long count = ((Number) row[2]).longValue();
      long[] counts = byTool.computeIfAbsent(toolName, key -> new long[5]);
      counts[0] += count;
      switch (status) {
        case APPROVED -> counts[1] += count;
        case REJECTED -> counts[2] += count;
        case EXPIRED -> counts[3] += count;
        case FAILED -> counts[4] += count;
        case PENDING -> {
          // still open, counted only toward "created"
        }
      }
    }
    return byTool.entrySet().stream()
        .map(
            entry -> {
              long[] counts = entry.getValue();
              return new AgentToolStat(
                  entry.getKey(), counts[0], counts[1], counts[2], counts[3], counts[4]);
            })
        .toList();
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
