package com.pravoos.user.identity.internal.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.brute-force")
public record BruteForceProperties(
    int maxAttempts,
    int maxAttemptsPerIp,
    Duration lockoutDuration,
    Duration attemptWindow,
    boolean failOpen) {

  private static final int DEFAULT_MAX_ATTEMPTS_PER_IP = 20;

  public BruteForceProperties {
    if (maxAttemptsPerIp <= 0) {
      maxAttemptsPerIp = DEFAULT_MAX_ATTEMPTS_PER_IP;
    }
  }
}
