package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

class DocxExportServiceTest {

  private final DocxExportService service = new DocxExportService();

  @Test
  void generatesDocxWithBoldTitleAndContentParagraphs() throws IOException {
    byte[] bytes = service.export("Заголовок документа", "Первая строка\nВторая строка");

    assertThat(bytes).isNotEmpty();
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
      List<XWPFParagraph> paragraphs = document.getParagraphs();
      assertThat(paragraphs).hasSize(3);
      assertThat(paragraphs.get(0).getText()).isEqualTo("Заголовок документа");
      assertThat(paragraphs.get(0).getRuns().get(0).isBold()).isTrue();
      assertThat(paragraphs.get(1).getText()).isEqualTo("Первая строка");
      assertThat(paragraphs.get(2).getText()).isEqualTo("Вторая строка");
    }
  }

  @Test
  void splitsContentOnCrlfAndLfLineBreaks() throws IOException {
    byte[] bytes = service.export("T", "a\r\nb\nc");

    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
      List<XWPFParagraph> paragraphs = document.getParagraphs();
      assertThat(paragraphs).hasSize(4);
      assertThat(paragraphs.get(1).getText()).isEqualTo("a");
      assertThat(paragraphs.get(2).getText()).isEqualTo("b");
      assertThat(paragraphs.get(3).getText()).isEqualTo("c");
    }
  }

  @Test
  void producesSingleEmptyParagraphForEmptyContent() throws IOException {
    byte[] bytes = service.export("T", "");

    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
      assertThat(document.getParagraphs()).hasSize(2);
      assertThat(document.getParagraphs().get(1).getText()).isEmpty();
    }
  }
}
