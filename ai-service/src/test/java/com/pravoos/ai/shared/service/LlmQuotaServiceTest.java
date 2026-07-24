package com.pravoos.ai.shared.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.shared.exception.LlmQuotaExceededException;
import com.pravoos.ai.shared.security.PlanLimitsProvider;
import com.pravoos.common.web.PlanLimits;
import java.time.LocalDate;
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

  @Mock private StringRedisTemplate redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  @Mock private PlanLimitsProvider planLimitsProvider;

  private LlmQuotaService llmQuotaService;
  private UUID lawyerId;

  @BeforeEach
  void setup() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    llmQuotaService = new LlmQuotaService(redisTemplate, planLimitsProvider);
    lawyerId = UUID.randomUUID();
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

  private String requestKey() {
    return "llm_quota:" + lawyerId + ":" + LocalDate.now();
  }

  private String tokenKey() {
    return "llm_tokens:" + lawyerId + ":" + LocalDate.now();
  }
}
