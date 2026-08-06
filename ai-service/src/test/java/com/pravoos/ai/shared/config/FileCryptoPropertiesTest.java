package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileCryptoPropertiesTest {

  @Test
  void hasKeyIsFalseWhenKeyIsNull() {
    assertThat(new FileCryptoProperties(null).hasKey()).isFalse();
  }

  @Test
  void hasKeyIsFalseWhenKeyIsBlank() {
    assertThat(new FileCryptoProperties("   ").hasKey()).isFalse();
  }

  @Test
  void hasKeyIsTrueWhenKeyIsPresent() {
    assertThat(new FileCryptoProperties("some-key").hasKey()).isTrue();
  }
}
