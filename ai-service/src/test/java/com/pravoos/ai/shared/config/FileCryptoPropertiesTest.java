package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileCryptoPropertiesTest {

  @Test
  void hasKeyIsFalseWhenKeyIsNull() {
    assertThat(FileCryptoProperties.ofSingleKey(null).hasKey()).isFalse();
  }

  @Test
  void hasKeyIsFalseWhenKeyIsBlank() {
    assertThat(FileCryptoProperties.ofSingleKey("   ").hasKey()).isFalse();
  }

  @Test
  void hasKeyIsTrueWhenKeyIsPresent() {
    assertThat(FileCryptoProperties.ofSingleKey("some-key").hasKey()).isTrue();
  }
}
