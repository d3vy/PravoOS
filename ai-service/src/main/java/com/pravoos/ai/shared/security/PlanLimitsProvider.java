package com.pravoos.ai.shared.security;

import com.pravoos.common.web.PlanLimits;
import com.pravoos.common.web.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PlanLimitsProvider {

  private static final String FALLBACK_PLAN_CODE = "DEFAULT";

  private final PlanLimits fallbackLimits;

  public PlanLimitsProvider(
      @Value("${llm.quota.daily-requests:200}") int fallbackDailyRequests,
      @Value("${llm.quota.daily-tokens:0}") long fallbackDailyTokens) {
    this.fallbackLimits =
        new PlanLimits(FALLBACK_PLAN_CODE, fallbackDailyRequests, fallbackDailyTokens);
  }

  public PlanLimits currentLimits() {
    return SecurityUtils.currentPlanLimits(SecurityContextHolder.getContext().getAuthentication())
        .orElse(fallbackLimits);
  }
}
