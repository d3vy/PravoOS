package com.pravoos.ai.shared.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReviewAnswerConfidenceTest {

  @Test
  void mapsKnownValuesCaseInsensitively() {
    assertThat(ReviewAnswerConfidence.fromString(" high ")).isEqualTo(ReviewAnswerConfidence.HIGH);
    assertThat(ReviewAnswerConfidence.fromString("Средняя"))
        .isEqualTo(ReviewAnswerConfidence.MEDIUM);
    assertThat(ReviewAnswerConfidence.fromString("none"))
        .isEqualTo(ReviewAnswerConfidence.NOT_FOUND);
  }

  @Test
  void fallsBackToLowForNullAndUnknownValues() {
    assertThat(ReviewAnswerConfidence.fromString(null)).isEqualTo(ReviewAnswerConfidence.LOW);
    assertThat(ReviewAnswerConfidence.fromString("CERTAIN")).isEqualTo(ReviewAnswerConfidence.LOW);
  }
}
