package com.pravoos.ai.document.internal.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pravoos.ai.shared.exception.DocumentProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

class DocumentParserTest {

  private final DocumentParser parser = new DocumentParser();

  private static byte[] pdfBytes(String text) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(50, 700);
        stream.showText(text);
        stream.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  private static byte[] docxBytes(String text) throws IOException {
    try (XWPFDocument document = new XWPFDocument()) {
      XWPFParagraph paragraph = document.createParagraph();
      XWPFRun run = paragraph.createRun();
      run.setText(text);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.write(out);
      return out.toByteArray();
    }
  }

  @Test
  void extractText_returnsRawBytesAsUtf8_forTxt() {
    String text = parser.extractText("Договор №1".getBytes(StandardCharsets.UTF_8), "txt");

    assertThat(text).isEqualTo("Договор №1");
  }

  @Test
  void extractText_extractsTextFromPdf() throws IOException {
    String text = parser.extractText(pdfBytes("Hello PDF"), "pdf");

    assertThat(text).contains("Hello PDF");
  }

  @Test
  void extractText_extractsTextFromDocx() throws IOException {
    String text = parser.extractText(docxBytes("Hello DOCX"), "docx");

    assertThat(text).contains("Hello DOCX");
  }

  @Test
  void extractText_isCaseInsensitiveToFileType() {
    String text = parser.extractText("plain text".getBytes(StandardCharsets.UTF_8), "TXT");

    assertThat(text).isEqualTo("plain text");
  }

  @Test
  void extractText_throwsProcessingException_whenFileTypeUnsupported() {
    assertThatThrownBy(() -> parser.extractText(new byte[] {1, 2, 3}, "exe"))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void extractText_throwsProcessingException_whenPdfContentInvalid() {
    assertThatThrownBy(
            () -> parser.extractText("not a pdf".getBytes(StandardCharsets.UTF_8), "pdf"))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void extractText_throwsUnwrappedPoiException_whenDocxContentInvalid() {
    // extractDocxText() only catches IOException; POI's own parse failure for a non-OOXML
    // payload is a RuntimeException that escapes unwrapped, unlike the PDF/malformed-type
    // paths above which do get converted to DocumentProcessingException. Documented as-is.
    assertThatThrownBy(
            () -> parser.extractText("not a docx".getBytes(StandardCharsets.UTF_8), "docx"))
        .isInstanceOf(org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException.class);
  }
}
