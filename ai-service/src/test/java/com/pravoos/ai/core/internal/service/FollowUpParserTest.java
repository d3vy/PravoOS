package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.core.internal.service.FollowUpParser.ParsedAnswer;
import org.junit.jupiter.api.Test;

class FollowUpParserTest {

  @Test
  void nullContentReturnsEmptyAnswerAndNoFollowUps() {
    ParsedAnswer result = FollowUpParser.parse(null);

    assertThat(result.answer()).isEmpty();
    assertThat(result.followUps()).isEmpty();
  }

  @Test
  void blankContentReturnsEmptyAnswerAndNoFollowUps() {
    ParsedAnswer result = FollowUpParser.parse("   ");

    assertThat(result.answer()).isEmpty();
    assertThat(result.followUps()).isEmpty();
  }

  @Test
  void contentWithoutDelimiterReturnsTrimmedAnswerAndNoFollowUps() {
    ParsedAnswer result = FollowUpParser.parse("  Просто ответ без секции  ");

    assertThat(result.answer()).isEqualTo("Просто ответ без секции");
    assertThat(result.followUps()).isEmpty();
  }

  @Test
  void parsesAnswerAndFollowUpsSeparatedByDelimiter() {
    String raw =
        "Основной ответ.\n##FOLLOWUPS##\n1. Первый вопрос\n2) Второй вопрос\n3. Третий вопрос";

    ParsedAnswer result = FollowUpParser.parse(raw);

    assertThat(result.answer()).isEqualTo("Основной ответ.");
    assertThat(result.followUps())
        .containsExactly("Первый вопрос", "Второй вопрос", "Третий вопрос");
  }

  @Test
  void stripsNumericPrefixesInVariousFormats() {
    String raw = "Ответ##FOLLOWUPS##1.Без пробела\n2) Со скобкой\n3   Только цифра с пробелами";

    ParsedAnswer result = FollowUpParser.parse(raw);

    assertThat(result.followUps())
        .containsExactly("Без пробела", "Со скобкой", "Только цифра с пробелами");
  }

  @Test
  void filtersOutBlankLinesInFollowUpSection() {
    String raw = "Ответ##FOLLOWUPS##1. Первый\n\n\n2. Второй";

    ParsedAnswer result = FollowUpParser.parse(raw);

    assertThat(result.followUps()).containsExactly("Первый", "Второй");
  }

  @Test
  void limitsFollowUpsToThree() {
    String raw = "Ответ##FOLLOWUPS##1. Первый\n2. Второй\n3. Третий\n4. Четвёртый\n5. Пятый";

    ParsedAnswer result = FollowUpParser.parse(raw);

    assertThat(result.followUps()).hasSize(3).containsExactly("Первый", "Второй", "Третий");
  }

  @Test
  void handlesEmptyFollowUpSectionAfterDelimiter() {
    String raw = "Ответ##FOLLOWUPS##   ";

    ParsedAnswer result = FollowUpParser.parse(raw);

    assertThat(result.answer()).isEqualTo("Ответ");
    assertThat(result.followUps()).isEmpty();
  }
}
