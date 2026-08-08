package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.core.internal.service.LegalActRegistry.ActMatch;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LegalActRegistryTest {

  private final LegalActRegistry registry = new LegalActRegistry();

  @Test
  void recognizesNullAndBlankWindowsAsEmpty() {
    assertThat(registry.recognize(null)).isEmpty();
    assertThat(registry.recognize("   ")).isEmpty();
  }

  @Test
  void recognizesUnrelatedTextAsEmpty() {
    assertThat(registry.recognize("Стороны заключили договор поставки")).isEmpty();
  }

  @Test
  void recognizesCivilProcedureCodeByAbbreviation() {
    Optional<ActMatch> match = registry.recognize("согласно ст. 131 ГПК РФ");

    assertThat(match).isPresent();
    assertThat(match.get().canonicalName()).isEqualTo("Гражданский процессуальный кодекс РФ");
    assertThat(match.get().matchedText()).isEqualToIgnoringCase("ГПК РФ");
  }

  @Test
  void recognizesArbitrationProcedureCodeByFullName() {
    Optional<ActMatch> match =
        registry.recognize("в соответствии с арбитражным процессуальным кодексом");

    assertThat(match).isPresent();
    assertThat(match.get().canonicalName()).isEqualTo("Арбитражный процессуальный кодекс РФ");
  }

  @Test
  void recognizesCivilCodeByBareAbbreviationWithWordBoundaries() {
    Optional<ActMatch> match = registry.recognize("ст. 421 ГК о свободе договора");

    assertThat(match).isPresent();
    assertThat(match.get().canonicalName()).isEqualTo("Гражданский кодекс РФ");
  }

  @Test
  void doesNotRecognizeBareAbbreviationEmbeddedInAnotherWord() {
    assertThat(registry.recognize("центрГКомпания обанкротилась")).isEmpty();
  }

  @Test
  void recognizesBankruptcyLawByNumberReference() {
    Optional<ActMatch> match = registry.recognize("предусмотрено 127-ФЗ");

    assertThat(match).isPresent();
    assertThat(match.get().canonicalName())
        .isEqualTo("Федеральный закон № 127-ФЗ «О несостоятельности (банкротстве)»");
  }

  @Test
  void recognizesFirstMatchingActWhenMultiplePatternsCouldApply() {
    Optional<ActMatch> match = registry.recognize("см. ГПК РФ и НК РФ одновременно");

    assertThat(match).isPresent();
    assertThat(match.get().canonicalName()).isEqualTo("Гражданский процессуальный кодекс РФ");
  }

  @Test
  void recognitionIsCaseInsensitive() {
    Optional<ActMatch> match = registry.recognize("трудовой кодекс регулирует эти отношения");

    assertThat(match).isPresent();
    assertThat(match.get().canonicalName()).isEqualTo("Трудовой кодекс РФ");
  }
}
