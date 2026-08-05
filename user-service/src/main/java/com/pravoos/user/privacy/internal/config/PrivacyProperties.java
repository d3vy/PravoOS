package com.pravoos.user.privacy.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pdn")
public record PrivacyProperties(
    String policyVersion, String operatorName, Integer accessDueDays, Integer erasureDueDays) {

  public String resolvedPolicyVersion() {
    return policyVersion == null || policyVersion.isBlank() ? "1.0" : policyVersion.trim();
  }

  public String resolvedOperatorName() {
    return operatorName == null || operatorName.isBlank() ? "PravoOS" : operatorName.trim();
  }

  public int resolvedAccessDueDays() {
    return accessDueDays == null ? 10 : accessDueDays;
  }

  public int resolvedErasureDueDays() {
    return erasureDueDays == null ? 30 : erasureDueDays;
  }
}
