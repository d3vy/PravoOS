package com.pravoos.llm.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.llm.config.InternalSecretProperties;
import org.junit.jupiter.api.Test;

class InternalSecretVerifierTest {

  private InternalSecretVerifier verifierWith(String configuredSecret) {
    return new InternalSecretVerifier(new InternalSecretProperties(configuredSecret));
  }

  @Test
  void matchesEqualSecret() {
    assertThat(verifierWith("s3cr3t").matches("s3cr3t")).isTrue();
  }

  @Test
  void rejectsWrongSecret() {
    assertThat(verifierWith("s3cr3t").matches("wrong")).isFalse();
  }

  @Test
  void rejectsNullPresentedSecret() {
    assertThat(verifierWith("s3cr3t").matches(null)).isFalse();
  }

  @Test
  void rejectsWhenConfiguredSecretBlank() {
    assertThat(verifierWith("   ").matches("   ")).isFalse();
  }

  @Test
  void rejectsWhenConfiguredSecretNull() {
    assertThat(verifierWith(null).matches("anything")).isFalse();
  }
}
