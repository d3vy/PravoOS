package com.pravoos.user.identity.internal.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.password-policy")
public record PasswordPolicyProperties(
    boolean hibpEnabled, Duration hibpTimeout, boolean hibpFailOpen) {
  public PasswordPolicyProperties {
    if (hibpTimeout == null) {
      hibpTimeout = Duration.ofSeconds(3);
    }
  }
}
