package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.shared.config.SignatureProperties.Diadoc;
import org.junit.jupiter.api.Test;

class SignaturePropertiesTest {

  @Test
  void defaultExpiryDaysFallsBackToThirtyWhenZeroOrNegative() {
    assertThat(new SignatureProperties(0, null, null).defaultExpiryDays()).isEqualTo(30);
    assertThat(new SignatureProperties(-5, null, null).defaultExpiryDays()).isEqualTo(30);
  }

  @Test
  void defaultExpiryDaysKeepsPositiveValue() {
    assertThat(new SignatureProperties(14, null, null).defaultExpiryDays()).isEqualTo(14);
  }

  @Test
  void diadocHasKeyIsFalseForNullOrBlankApiKey() {
    assertThat(new Diadoc("https://diadoc", null).hasKey()).isFalse();
    assertThat(new Diadoc("https://diadoc", "  ").hasKey()).isFalse();
  }

  @Test
  void diadocHasKeyIsTrueWhenApiKeyPresent() {
    assertThat(new Diadoc("https://diadoc", "api-key").hasKey()).isTrue();
  }
}
