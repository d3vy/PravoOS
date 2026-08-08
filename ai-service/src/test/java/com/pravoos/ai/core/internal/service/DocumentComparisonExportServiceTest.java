package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.internal.dto.ComparisonExportFile;
import com.pravoos.ai.core.internal.dto.DiffChange;
import com.pravoos.ai.core.internal.dto.DiffSegment;
import com.pravoos.ai.core.internal.dto.DocumentComparisonDto;
import com.pravoos.ai.shared.model.enums.ContractRiskLevel;
import com.pravoos.ai.shared.model.enums.DiffChangeType;
import com.pravoos.ai.shared.model.enums.DiffSegmentType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentComparisonExportServiceTest {

  @Mock private DocumentComparisonService comparisonService;

  private DocumentComparisonExportService exportService;

  private final UUID comparisonId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    exportService = new DocumentComparisonExportService(comparisonService);
  }

  private String extractText(byte[] bytes) throws IOException {
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
      return extractor.getText();
    }
  }

  private DocumentComparisonDto dto(List<DiffChange> changes) {
    return new DocumentComparisonDto(
        comparisonId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Договор v1",
        "Договор v2",
        "Изменена сумма и срок",
        (short) 65,
        changes.size(),
        (int) changes.stream().filter(c -> c.riskLevel() == ContractRiskLevel.HIGH).count(),
        changes,
        LocalDateTime.of(2026, 3, 1, 10, 0));
  }

  @Test
  void exportDocxWritesHeaderLegendAndSummary() throws IOException {
    when(comparisonService.get(comparisonId, lawyerId, List.of())).thenReturn(dto(List.of()));

    ComparisonExportFile file = exportService.exportDocx(comparisonId, lawyerId, List.of());

    assertThat(file.contentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    String text = extractText(file.content());
    assertThat(text).contains("Редлайн: сравнение версий");
    assertThat(text).contains("Договор v1");
    assertThat(text).contains("Договор v2");
    assertThat(text).contains("Изменена сумма и срок");
    assertThat(text).contains("Различий между версиями не обнаружено.");
    assertThat(text).contains("Удалено");
    assertThat(text).contains("Добавлено");
  }

  @Test
  void exportDocxWritesInlineSegmentsForModifiedChanges() throws IOException {
    DiffChange change =
        new DiffChange(
            1,
            DiffChangeType.MODIFIED,
            "старая сумма",
            "новая сумма",
            ContractRiskLevel.HIGH,
            "Существенное изменение",
            List.of(
                new DiffSegment(DiffSegmentType.REMOVED, "старая"),
                new DiffSegment(DiffSegmentType.EQUAL, " сумма"),
                new DiffSegment(DiffSegmentType.ADDED, " новая")));
    when(comparisonService.get(comparisonId, lawyerId, List.of())).thenReturn(dto(List.of(change)));

    ComparisonExportFile file = exportService.exportDocx(comparisonId, lawyerId, List.of());

    String text = extractText(file.content());
    assertThat(text).contains("Изменено");
    assertThat(text).contains("риск: " + ContractRiskLevel.HIGH.displayName());
    assertThat(text).contains("Существенное изменение");
  }

  @Test
  void exportDocxWritesBlockTextsForAddedAndRemovedChanges() throws IOException {
    DiffChange added =
        new DiffChange(1, DiffChangeType.ADDED, null, "Новый пункт 5.1", null, null, List.of());
    DiffChange removed =
        new DiffChange(2, DiffChangeType.REMOVED, "Старый пункт 3.2", null, null, null, List.of());
    when(comparisonService.get(comparisonId, lawyerId, List.of()))
        .thenReturn(dto(List.of(added, removed)));

    ComparisonExportFile file = exportService.exportDocx(comparisonId, lawyerId, List.of());

    String text = extractText(file.content());
    assertThat(text).contains("Новый пункт 5.1");
    assertThat(text).contains("Старый пункт 3.2");
  }

  @Test
  void exportDocxSanitizesFileNameFromDocumentTitles() {
    when(comparisonService.get(comparisonId, lawyerId, List.of())).thenReturn(dto(List.of()));

    ComparisonExportFile file = exportService.exportDocx(comparisonId, lawyerId, List.of());

    assertThat(file.fileName()).isEqualTo("redline-Договор v1-Договор v2.docx");
  }

  @Test
  void exportDocxTruncatesLongFileNameTo80Characters() {
    String longTitle = "A".repeat(60);
    DocumentComparisonDto longTitles =
        new DocumentComparisonDto(
            comparisonId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            longTitle,
            longTitle,
            "Резюме",
            (short) 0,
            0,
            0,
            List.of(),
            LocalDateTime.of(2026, 3, 1, 10, 0));
    when(comparisonService.get(comparisonId, lawyerId, List.of())).thenReturn(longTitles);

    ComparisonExportFile file = exportService.exportDocx(comparisonId, lawyerId, List.of());

    String base =
        file.fileName().substring("redline-".length(), file.fileName().length() - ".docx".length());
    assertThat(base).hasSize(80);
  }
}
