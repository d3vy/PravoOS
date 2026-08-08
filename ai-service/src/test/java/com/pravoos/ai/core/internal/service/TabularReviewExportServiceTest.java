package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.internal.dto.ReviewExportFile;
import com.pravoos.ai.core.internal.model.entity.TabularReview;
import com.pravoos.ai.core.internal.model.entity.TabularReviewCell;
import com.pravoos.ai.core.internal.model.entity.TabularReviewDocument;
import com.pravoos.ai.shared.exception.InvalidExportFormatException;
import com.pravoos.ai.shared.model.enums.ReviewAnswerConfidence;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TabularReviewExportServiceTest {

  @Mock private TabularReviewService reviewService;

  private TabularReviewExportService exportService;

  private final UUID reviewId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    exportService = new TabularReviewExportService(reviewService);
  }

  private TabularReview review() {
    TabularReview review = new TabularReview();
    review.setTitle("Разбор договоров");
    review.setQuestions(List.of("Кто стороны?", "Сумма?"));
    return review;
  }

  private TabularReviewDocument document() {
    TabularReviewDocument document = new TabularReviewDocument();
    document.setDocumentId(documentId);
    document.setDocumentTitle("Договор аренды");
    return document;
  }

  private TabularReviewCell cell(int questionIndex, String answer) {
    TabularReviewCell cell = new TabularReviewCell();
    cell.setDocumentId(documentId);
    cell.setQuestionIndex(questionIndex);
    cell.setAnswer(answer);
    cell.setConfidence(ReviewAnswerConfidence.HIGH);
    cell.setCitations(List.of());
    return cell;
  }

  private void stub() {
    when(reviewService.requireVisibleReview(reviewId, lawyerId, List.of())).thenReturn(review());
    when(reviewService.documentsOf(reviewId)).thenReturn(List.of(document()));
    when(reviewService.cellsOf(reviewId))
        .thenReturn(List.of(cell(0, "ООО Ромашка"), cell(1, "150000 руб.")));
  }

  @Test
  void exportRejectsUnsupportedFormat() {
    stub();

    assertThatThrownBy(() -> exportService.export(reviewId, "csv", lawyerId, List.of()))
        .isInstanceOf(InvalidExportFormatException.class);
  }

  @Test
  void exportDefaultsToXlsxWhenFormatBlank() throws IOException {
    stub();

    ReviewExportFile file = exportService.export(reviewId, null, lawyerId, List.of());

    assertThat(file.fileName()).endsWith(".xlsx");
    assertThat(file.contentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
  }

  @Test
  void exportXlsxWritesHeaderAndAnswerCells() throws IOException {
    stub();

    ReviewExportFile file = exportService.export(reviewId, "xlsx", lawyerId, List.of());

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
      Sheet sheet = workbook.getSheetAt(0);
      Row header = sheet.getRow(0);
      assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Документ");
      assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Кто стороны?");
      Row dataRow = sheet.getRow(1);
      assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("Договор аренды");
      assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("ООО Ромашка");
      assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("150000 руб.");
    }
  }

  @Test
  void exportXlsxUsesEmptyCellPlaceholderForMissingAnswers() throws IOException {
    when(reviewService.requireVisibleReview(reviewId, lawyerId, List.of())).thenReturn(review());
    when(reviewService.documentsOf(reviewId)).thenReturn(List.of(document()));
    when(reviewService.cellsOf(reviewId)).thenReturn(List.of());

    ReviewExportFile file = exportService.export(reviewId, "xlsx", lawyerId, List.of());

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
      Row dataRow = workbook.getSheetAt(0).getRow(1);
      assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("—");
    }
  }

  @Test
  void exportDocxWritesTitleAndTable() throws IOException {
    stub();

    ReviewExportFile file = exportService.export(reviewId, "DOCX", lawyerId, List.of());

    assertThat(file.fileName()).endsWith(".docx");
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.content()));
        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
      String text = extractor.getText();
      assertThat(text).contains("Разбор договоров");
      assertThat(text).contains("Договор аренды");
      assertThat(text).contains("ООО Ромашка");
    }
  }

  @Test
  void exportUsesFallbackFileNameWhenTitleSanitizesToEmpty() {
    TabularReview review = new TabularReview();
    review.setTitle("   ");
    review.setQuestions(List.of("Вопрос?"));
    when(reviewService.requireVisibleReview(reviewId, lawyerId, List.of())).thenReturn(review);
    when(reviewService.documentsOf(reviewId)).thenReturn(List.of());
    when(reviewService.cellsOf(reviewId)).thenReturn(List.of());

    ReviewExportFile file = exportService.export(reviewId, "xlsx", lawyerId, List.of());

    assertThat(file.fileName()).isEqualTo("review.xlsx");
  }
}
