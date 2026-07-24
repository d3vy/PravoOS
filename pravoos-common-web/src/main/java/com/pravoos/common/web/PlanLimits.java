package com.pravoos.common.web;

public record PlanLimits(String code, int dailyRequests, long dailyTokens) {

  public PlanLimits {
    code = code == null || code.isBlank() ? "UNKNOWN" : code;
    dailyRequests = Math.max(dailyRequests, 0);
    dailyTokens = Math.max(dailyTokens, 0L);
  }

  public boolean quotaDisabled() {
    return dailyRequests <= 0 && dailyTokens <= 0;
  }
}
