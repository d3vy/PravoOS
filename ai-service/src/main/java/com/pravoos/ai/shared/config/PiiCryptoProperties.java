package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pii.crypto")
public record PiiCryptoProperties(String key) {
  public boolean hasKey() {
    return key != null && !key.isBlank();
  }
}
