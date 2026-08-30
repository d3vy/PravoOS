package com.pravoos.ai.shared.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.shared.exception.LlmQuotaExceededException;
import com.pravoos.ai.shared.security.CurrentTenantProvider;
import com.pravoos.ai.shared.security.PlanLimitsProvider;
import com.pravoos.common.web.PlanLimits;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LlmQuotaServiceTest {

  private static final int ORG_MULTIPLIER = 10;

  @Mock private StringRedisTemplate redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  @Mock private PlanLimitsProvider planLimitsProvider;

  @Mock private CurrentTenantProvider currentTenantProvider;

  private LlmQuotaService llmQuotaService;
  private UUID lawyerId;
  private UUID orgId;

  @BeforeEach
  void setup() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    llmQuotaService =
        new LlmQuotaService(
            redisTemplate, planLimitsProvider, currentTenantProvider, ORG_MULTIPLIER);
    lawyerId = UUID.randomUUID();
    orgId = UUID.randomUUID();
    when(currentTenantProvider.currentOrgId()).thenReturn(Optional.empty());
  }

  @Test
  void quotaExceededWhenRequestsReachPlanLimit() {
    when(planLimitsProvider.currentLimits()).thenReturn(new PlanLimits("FREE", 20, 20000L));
    when(valueOperations.get(requestKey())).thenReturn("20");

    assertThatThrownBy(() -> llmQuotaService.assertWithinQuota(lawyerId))
        .isInstanceOf(LlmQuotaExceededException.class);
  }

  @Test
  void sameUsageStaysWithinQuotaOnHigherPlan() {
    when(planLimitsProvider.currentLimits()).thenReturn(new PlanLimits("SOLO", 200, 200000L));
    when(valueOperations.get(requestKey())).thenReturn("20");
    when(valueOperations.get(tokenKey())).thenReturn("1000");

    assertThatCode(() -> llmQuotaService.assertWithinQuota(lawyerId)).doesNotThrowAnyException();
  }

  @Test
  void quotaExceededWhenTokensReachPlanBudget() {
    when(planLimitsProvider.currentLimits()).thenReturn(new PlanLimits("FREE", 20, 20000L));
    when(valueOperations.get(requestKey())).thenReturn("5");
    when(valueOperations.get(tokenKey())).thenReturn("20000");

    assertThatThrownBy(() -> llmQuotaService.assertWithinQuota(lawyerId))
        .isInstanceOf(LlmQuotaExceededException.class);
  }

  @Test
  void quotaDisabledWhenPlanHasNoLimits() {
    when(planLimitsProvider.currentLimits()).thenReturn(new PlanLimits("UNLIMITED", 0, 0L));

    llmQuotaService.assertWithinQuota(lawyerId);
    llmQuotaService.recordUsage(lawyerId, 500L);

    verify(valueOperations, never()).get(requestKey());
    verify(valueOperations, never()).increment(requestKey(), 1L);
  }

  @Test
  void recordUsageIncrementsRequestAndTokenCounters() {
    when(planLimitsProvider.currentLimits()).thenReturn(new PlanLimits("SOLO", 200, 200000L));

    llmQuotaService.recordUsage(lawyerId, 750L);

    verify(valueOperations).increment(requestKey(), 1L);
    verify(valueOperations).increment(tokenKey(), 750L);
  }

  @Test
  void recordUsageAlsoChargesTheOrgCountersWhenTenantIsResolved() {
    when(planLimitsProvider.currentLimits()).thenReturn(new PlanLimits("SOLO", 200, 200000L));
    withOrgContext();

    llmQuotaService.recordUsage(lawyerId, 750L, 3);

    verify(valueOperations).increment(requestKey(), 3L);
    verify(valueOperations).increment(orgRequestKey(), 3L);
    verify(valueOperations).increment(tokenKey(), 750L);
    verify(valueOperations).increment(orgTokenKey(), 750L);
  }

  @Test
  void orgQuotaExceededEvenWhenTheLawyerIsWellWithinTheirOwnLimit() {
    when(planLimitsProvider.currentLimits()).thenReturn(new PlanLimits("SOLO", 200, 0L));
    withOrgContext();
    when(valueOperations.get(requestKey())).thenReturn("1");
    when(valueOperations.get(orgRequestKey())).thenReturn(String.valueOf(200 * ORG_MULTIPLIER));

    assertThatThrownBy(() -> llmQuotaService.assertWithinQuota(lawyerId))
        .isInstanceOf(LlmQuotaExceededException.class)
        .extracting(exception -> ((LlmQuotaExceededException) exception).getCode())
        .isEqualTo("LLM_ORG_QUOTA_EXCEEDED");
  }

  @Test
  void orgTokenBudgetExceededStopsTheRequest() {
    when(planLimitsProvider.currentLimits()).thenReturn(new PlanLimits("SOLO", 200, 200000L));
    withOrgContext();
    when(valueOperations.get(requestKey())).thenReturn("1");
    when(valueOperations.get(tokenKey())).thenReturn("10");
    when(valueOperations.get(orgRequestKey())).thenReturn("10");
    when(valueOperations.get(orgTokenKey())).thenReturn(String.valueOf(200000L * ORG_MULTIPLIER));

    assertThatThrownBy(() -> llmQuotaService.assertWithinQuota(lawyerId))
        .isInstanceOf(LlmQuotaExceededException.class);
  }

  @Test
  void headroomCheckAccountsForTheWholeAgentTurnAgainstTheOrgLimit() {
    when(planLimitsProvider.currentLimits()).thenReturn(new PlanLimits("SOLO", 200, 0L));
    withOrgContext();
    when(valueOperations.get(requestKey())).thenReturn("1");
    when(valueOperations.get(orgRequestKey())).thenReturn(String.valueOf(200 * ORG_MULTIPLIER - 4));

    assertThatThrownBy(() -> llmQuotaService.assertQuotaHeadroom(lawyerId, 8))
        .isInstanceOf(LlmQuotaExceededException.class);
  }

  @Test
  void orgQuotaIsSkippedWhenTheMultiplierIsZero() {
    llmQuotaService =
        new LlmQuotaService(redisTemplate, planLimitsProvider, currentTenantProvider, 0);
    when(planLimitsProvider.currentLimits()).thenReturn(new PlanLimits("SOLO", 200, 0L));
    withOrgContext();
    when(valueOperations.get(requestKey())).thenReturn("1");

    llmQuotaService.assertWithinQuota(lawyerId);
    llmQuotaService.recordUsage(lawyerId, 100L);

    verify(valueOperations, never()).get(orgRequestKey());
    verify(valueOperations, never()).increment(orgRequestKey(), 1L);
  }

  private void withOrgContext() {
    when(currentTenantProvider.currentOrgId()).thenReturn(Optional.of(orgId));
  }

  private String requestKey() {
    return "llm_quota:" + lawyerId + ":" + LocalDate.now();
  }

  private String tokenKey() {
    return "llm_tokens:" + lawyerId + ":" + LocalDate.now();
  }

  private String orgRequestKey() {
    return "llm_quota_org:" + orgId + ":" + LocalDate.now();
  }

  private String orgTokenKey() {
    return "llm_tokens_org:" + orgId + ":" + LocalDate.now();
  }
}
