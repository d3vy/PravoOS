package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PersonalDataConsentPropertiesTest {

  @Test
  void resolvedVersionFallsBackToDefaultWhenNull() {
    assertThat(new PersonalDataConsentProperties(null).resolvedVersion()).isEqualTo("1.0");
  }

  @Test
  void resolvedVersionFallsBackToDefaultWhenBlank() {
    assertThat(new PersonalDataConsentProperties("   ").resolvedVersion()).isEqualTo("1.0");
  }

  @Test
  void resolvedVersionTrimsExplicitValue() {
    assertThat(new PersonalDataConsentProperties("  2.1  ").resolvedVersion()).isEqualTo("2.1");
  }
}
