package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "signature")
public record SignatureProperties(int defaultExpiryDays, Diadoc diadoc, Cms cms) {
  public SignatureProperties {
    if (defaultExpiryDays <= 0) {
      defaultExpiryDays = 30;
    }
    if (cms == null) {
      cms = new Cms(null);
    }
  }

  public record Diadoc(String baseUrl, String apiKey) {
    public boolean hasKey() {
      return apiKey != null && !apiKey.isBlank();
    }
  }

  public record Cms(String trustedCaPath) {}
}
