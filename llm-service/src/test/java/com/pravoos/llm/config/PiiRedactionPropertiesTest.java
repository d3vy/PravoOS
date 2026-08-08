package com.pravoos.llm.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.llm.pii.PiiPattern;
import org.junit.jupiter.api.Test;

class PiiRedactionPropertiesTest {

  @Test
  void enabledDefaultsToTrueWhenNull() {
    PiiRedactionProperties properties = new PiiRedactionProperties(null, null, null);

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.isEmbeddingsEnabled()).isTrue();
  }

  @Test
  void embeddingsDisabledWhenRedactionDisabledEvenIfEmbeddingsFlagTrue() {
    PiiRedactionProperties properties = new PiiRedactionProperties(false, true, null);

    assertThat(properties.isEmbeddingsEnabled()).isFalse();
  }

  @Test
  void nullCategoriesEnablesAllPatterns() {
    PiiRedactionProperties properties = new PiiRedactionProperties(true, true, null);

    for (PiiPattern pattern : PiiPattern.values()) {
      assertThat(properties.isCategoryEnabled(pattern)).isTrue();
    }
  }

  @Test
  void blankCategoriesEnablesAllPatterns() {
    PiiRedactionProperties properties = new PiiRedactionProperties(true, true, "   ");

    assertThat(properties.isCategoryEnabled(PiiPattern.EMAIL)).isTrue();
  }

  @Test
  void explicitAllKeywordEnablesAllPatterns() {
    PiiRedactionProperties properties = new PiiRedactionProperties(true, true, "all");

    assertThat(properties.isCategoryEnabled(PiiPattern.CARD)).isTrue();
  }

  @Test
  void matchesByEnumName() {
    PiiRedactionProperties properties = new PiiRedactionProperties(true, true, "email, card");

    assertThat(properties.isCategoryEnabled(PiiPattern.EMAIL)).isTrue();
    assertThat(properties.isCategoryEnabled(PiiPattern.CARD)).isTrue();
    assertThat(properties.isCategoryEnabled(PiiPattern.INN)).isFalse();
  }

  @Test
  void matchesByLabelGroupingMultiplePatternsUnderSameLabel() {
    PiiRedactionProperties properties = new PiiRedactionProperties(true, true, "NAME");

    assertThat(properties.isCategoryEnabled(PiiPattern.FULL_NAME)).isTrue();
    assertThat(properties.isCategoryEnabled(PiiPattern.NAME_WITH_INITIALS)).isTrue();
    assertThat(properties.isCategoryEnabled(PiiPattern.PATRONYMIC_NAME)).isTrue();
    assertThat(properties.isCategoryEnabled(PiiPattern.EMAIL)).isFalse();
  }

  @Test
  void unknownCategoryNameEnablesNothing() {
    PiiRedactionProperties properties = new PiiRedactionProperties(true, true, "not-a-category");

    for (PiiPattern pattern : PiiPattern.values()) {
      assertThat(properties.isCategoryEnabled(pattern)).isFalse();
    }
  }
}
