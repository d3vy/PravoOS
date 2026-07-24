package com.pravoos.llm.security;

import com.pravoos.llm.config.InternalSecretProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class InternalSecretVerifier {

  private final InternalSecretProperties secretProperties;

  public InternalSecretVerifier(InternalSecretProperties secretProperties) {
    this.secretProperties = secretProperties;
  }

  public boolean matches(String secret) {
    String configuredSecret = secretProperties.secret();
    if (configuredSecret == null || configuredSecret.isBlank()) {
      return false;
    }
    return secret != null && MessageDigest.isEqual(sha256(configuredSecret), sha256(secret));
  }

  private byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
