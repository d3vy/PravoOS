package com.pravoos.user.shared.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("docker")
public class InternalSecretGuard {

  private final String internalSecret;

  public InternalSecretGuard(@Value("${internal.secret:}") String internalSecret) {
    this.internalSecret = internalSecret;
  }

  @PostConstruct
  void verifyInternalSecretPresent() {
    if (internalSecret == null || internalSecret.isBlank()) {
      throw new IllegalStateException(
          "INTERNAL_SERVICE_SECRET must be set in production (profile 'docker'). "
              + "Without it, /internal/** endpoints would accept any request and expose "
              + "application and Telegram-binding data.");
    }
  }
}
