package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pdn.consent")
public record PersonalDataConsentProperties(String currentVersion) {
  public String resolvedVersion() {
    return currentVersion == null || currentVersion.isBlank() ? "1.0" : currentVersion.trim();
  }
}
