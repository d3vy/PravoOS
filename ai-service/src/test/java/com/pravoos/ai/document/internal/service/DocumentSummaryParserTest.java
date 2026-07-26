package com.pravoos.ai.document.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.shared.config.DocumentSummaryProperties;
import org.junit.jupiter.api.Test;

class DocumentSummaryParserTest {

  private final DocumentSummaryParser parser =
      new DocumentSummaryParser(
          new ObjectMapper(), new DocumentSummaryProperties(true, 24000, 3, 40, 20, 900));

  @Test
  void parsesPlainJsonObject() {
    DocumentSummaryDraft draft =
        parser.parse("{\"summary\": \"Договор поставки\", \"keyPoints\": [\"Срок 30 дней\"]}");

    assertThat(draft.summary()).isEqualTo("Договор поставки");
    assertThat(draft.keyPoints()).containsExactly("Срок 30 дней");
  }

  @Test
  void toleratesMarkdownFencesAndSurroundingText() {
    String raw =
        """
            Вот результат:
            ```json
            {"summary": "Аренда", "keyPoints": ["Арендодатель — ООО"]}
            ```
            Готово.
            """;

    DocumentSummaryDraft draft = parser.parse(raw);

    assertThat(draft.summary()).isEqualTo("Аренда");
    assertThat(draft.keyPoints()).containsExactly("Арендодатель — ООО");
  }

  @Test
  void capsKeyPointsAndDropsDuplicatesAndBlanks() {
    DocumentSummaryDraft draft =
        parser.parse(
            "{\"summary\": \"S\", \"keyPoints\": [\"a\", \"a\", \"  \", \"b\", \"c\", \"d\"]}");

    assertThat(draft.keyPoints()).containsExactly("a", "b", "c");
  }

  @Test
  void truncatesOverlongSummaryAndKeyPoints() {
    String longText = "я".repeat(100);
    DocumentSummaryDraft draft =
        parser.parse("{\"summary\": \"" + longText + "\", \"keyPoints\": [\"" + longText + "\"]}");

    assertThat(draft.summary()).hasSize(40);
    assertThat(draft.keyPoints().get(0)).hasSize(20);
  }

  @Test
  void returnsEmptyOnNonJsonResponse() {
    assertThat(parser.parse("не могу составить содержание").isEmpty()).isTrue();
    assertThat(parser.parse(null).isEmpty()).isTrue();
  }

  @Test
  void returnsEmptyWhenModelReportsNoContent() {
    assertThat(parser.parse("{\"summary\": \"\", \"keyPoints\": []}").isEmpty()).isTrue();
  }

  @Test
  void ignoresNonTextKeyPoints() {
    DocumentSummaryDraft draft =
        parser.parse("{\"summary\": \"S\", \"keyPoints\": [{\"a\": 1}, \"валидный\"]}");

    assertThat(draft.keyPoints()).containsExactly("валидный");
  }
}
