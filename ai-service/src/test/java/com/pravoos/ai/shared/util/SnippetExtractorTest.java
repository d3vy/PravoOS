package com.pravoos.ai.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SnippetExtractorTest {

  @Test
  void returnsSnippetAroundMatchWithEllipsisOnBothSides() {
    String content =
        "Настоящим уведомляем стороны о переносе судебного заседания на следующую неделю по делу";

    String snippet = SnippetExtractor.around(content, "судебного", 10);

    assertThat(snippet).contains("судебного");
    assertThat(snippet).startsWith("…");
    assertThat(snippet).endsWith("…");
  }

  @Test
  void collapsesWhitespace() {
    String snippet = SnippetExtractor.around("много    пробелов   тут", "пробелов", 50);

    assertThat(snippet).isEqualTo("много пробелов тут");
  }

  @Test
  void noMatchFallsBackToHead() {
    String snippet = SnippetExtractor.around("короткий текст", "отсутствует", 3);

    assertThat(snippet).startsWith("кор");
  }

  @Test
  void nullContentReturnsNull() {
    assertThat(SnippetExtractor.around(null, "x", 5)).isNull();
  }
}
