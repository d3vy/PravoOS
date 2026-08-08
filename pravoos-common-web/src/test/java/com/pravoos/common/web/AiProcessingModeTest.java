package com.pravoos.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiProcessingModeTest {

  @Test
  void fromClaim_nullOrBlank_returnsDefault() {
    assertThat(AiProcessingMode.fromClaim(null)).isEqualTo(AiProcessingMode.DEFAULT);
    assertThat(AiProcessingMode.fromClaim("  ")).isEqualTo(AiProcessingMode.DEFAULT);
  }

  @Test
  void fromClaim_unknownValue_returnsDefault() {
    assertThat(AiProcessingMode.fromClaim("not_a_mode")).isEqualTo(AiProcessingMode.DEFAULT);
  }

  @Test
  void fromClaim_parsesCaseInsensitivelyAndTrims() {
    assertThat(AiProcessingMode.fromClaim(" cross_border "))
        .isEqualTo(AiProcessingMode.CROSS_BORDER);
    assertThat(AiProcessingMode.fromClaim("disabled")).isEqualTo(AiProcessingMode.DISABLED);
    assertThat(AiProcessingMode.fromClaim("RU_ONLY")).isEqualTo(AiProcessingMode.RU_ONLY);
  }

  @Test
  void allowsCrossBorderTransfer_onlyTrueForCrossBorder() {
    assertThat(AiProcessingMode.CROSS_BORDER.allowsCrossBorderTransfer()).isTrue();
    assertThat(AiProcessingMode.RU_ONLY.allowsCrossBorderTransfer()).isFalse();
    assertThat(AiProcessingMode.DISABLED.allowsCrossBorderTransfer()).isFalse();
  }

  @Test
  void allowsAi_falseOnlyForDisabled() {
    assertThat(AiProcessingMode.DISABLED.allowsAi()).isFalse();
    assertThat(AiProcessingMode.RU_ONLY.allowsAi()).isTrue();
    assertThat(AiProcessingMode.CROSS_BORDER.allowsAi()).isTrue();
  }
}
