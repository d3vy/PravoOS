package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.internal.dto.AiStatsResponse;
import com.pravoos.ai.core.internal.dto.WorkflowStat;
import com.pravoos.ai.core.internal.model.entity.AiResponse;
import com.pravoos.ai.core.internal.repository.jpa.AiResponseRepository;
import com.pravoos.ai.shared.model.enums.BankruptcyWorkflow;
import com.pravoos.ai.shared.model.enums.TrustMetric;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

  @Mock private AiResponseRepository aiResponseRepository;
  @Mock private TrustMetricsRecorder trustMetricsRecorder;

  private AdminStatsService service;

  @BeforeEach
  void setUp() {
    service = new AdminStatsService(aiResponseRepository, trustMetricsRecorder);
  }

  private Map<TrustMetric, Long> trustTotals() {
    Map<TrustMetric, Long> totals = new EnumMap<>(TrustMetric.class);
    for (TrustMetric metric : TrustMetric.values()) {
      totals.put(metric, 0L);
    }
    return totals;
  }

  @Test
  void getStatsAggregatesCountsAndTrustMetrics() {
    when(aiResponseRepository.count()).thenReturn(120L);
    when(aiResponseRepository.countRated()).thenReturn(80L);
    when(aiResponseRepository.countPositive()).thenReturn(60L);
    when(aiResponseRepository.countNegative()).thenReturn(20L);

    Map<TrustMetric, Long> totals = trustTotals();
    totals.put(TrustMetric.GUARD_PASS, 90L);
    totals.put(TrustMetric.GUARD_BLOCK, 10L);
    totals.put(TrustMetric.CITATION_VERIFIED, 15L);
    totals.put(TrustMetric.CITATION_NOT_FOUND, 3L);
    totals.put(TrustMetric.CITATION_OUTDATED, 2L);
    totals.put(TrustMetric.CITATION_UNVERIFIED, 1L);
    when(trustMetricsRecorder.totals()).thenReturn(totals);

    when(aiResponseRepository.aggregateByWorkflow())
        .thenReturn(
            List.of(
                new Object[] {"DEBTOR_SOLVENCY_ANALYSIS", 5L, 4.5},
                new Object[] {"UNKNOWN_WORKFLOW_ID", 2L, null}));

    AiStatsResponse stats = service.getStats();

    assertThat(stats.totalResponses()).isEqualTo(120L);
    assertThat(stats.ratedResponses()).isEqualTo(80L);
    assertThat(stats.positiveRatings()).isEqualTo(60L);
    assertThat(stats.negativeRatings()).isEqualTo(20L);
    assertThat(stats.guardChecks()).isEqualTo(100L);
    assertThat(stats.guardRefusals()).isEqualTo(10L);
    assertThat(stats.citationsChecked()).isEqualTo(21L);
    assertThat(stats.citationsVerified()).isEqualTo(15L);

    assertThat(stats.workflows()).hasSize(2);
    WorkflowStat known = stats.workflows().get(0);
    assertThat(known.workflowId()).isEqualTo("DEBTOR_SOLVENCY_ANALYSIS");
    assertThat(known.workflowName())
        .isEqualTo(BankruptcyWorkflow.DEBTOR_SOLVENCY_ANALYSIS.displayName());
    assertThat(known.count()).isEqualTo(5L);
    assertThat(known.avgRating()).isEqualTo(4.5);

    WorkflowStat unknown = stats.workflows().get(1);
    assertThat(unknown.workflowName()).isEqualTo("UNKNOWN_WORKFLOW_ID");
    assertThat(unknown.avgRating()).isNull();
  }

  @Test
  void getStatsReturnsZeroWorkflowsWhenNoResponsesExist() {
    when(aiResponseRepository.count()).thenReturn(0L);
    when(aiResponseRepository.countRated()).thenReturn(0L);
    when(aiResponseRepository.countPositive()).thenReturn(0L);
    when(aiResponseRepository.countNegative()).thenReturn(0L);
    when(trustMetricsRecorder.totals()).thenReturn(trustTotals());
    when(aiResponseRepository.aggregateByWorkflow()).thenReturn(List.of());

    AiStatsResponse stats = service.getStats();

    assertThat(stats.totalResponses()).isZero();
    assertThat(stats.guardChecks()).isZero();
    assertThat(stats.citationsChecked()).isZero();
    assertThat(stats.workflows()).isEmpty();
  }

  @Test
  void getRecentResponsesMapsToDto() {
    AiResponse response = new AiResponse();
    setId(response, UUID.randomUUID());
    response.setCaseId(UUID.randomUUID());
    response.setWorkflowId("CREDITOR_CLAIMS");
    response.setQuery("Вопрос");
    response.setResult("Результат");
    when(aiResponseRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(response));

    List<AiResponseDto> recent = service.getRecentResponses();

    assertThat(recent).hasSize(1);
    assertThat(recent.get(0).query()).isEqualTo("Вопрос");
    assertThat(recent.get(0).workflowName())
        .isEqualTo(BankruptcyWorkflow.CREDITOR_CLAIMS.displayName());
  }

  @Test
  void getRecentResponsesReturnsEmptyListWhenNoneExist() {
    when(aiResponseRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of());

    assertThat(service.getRecentResponses()).isEmpty();
  }

  private void setId(AiResponse response, UUID id) {
    try {
      java.lang.reflect.Field field = AiResponse.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(response, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
