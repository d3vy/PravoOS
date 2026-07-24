package com.pravoos.user.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

  @Test
  void trimsAndLowercases() {
    assertThat(EmailNormalizer.normalize("  Ivan.Petrov@Example.COM "))
        .isEqualTo("ivan.petrov@example.com");
  }

  @Test
  void nullBecomesEmptyString() {
    assertThat(EmailNormalizer.normalize(null)).isEmpty();
  }
}
