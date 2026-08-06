package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.internal.dto.ComparisonExportFile;
import com.pravoos.ai.core.internal.dto.DiffChange;
import com.pravoos.ai.core.internal.dto.DiffSegment;
import com.pravoos.ai.core.internal.dto.DocumentComparisonDto;
import com.pravoos.ai.shared.exception.CaseExportException;
import com.pravoos.ai.shared.model.enums.DiffChangeType;
import com.pravoos.ai.shared.util.ExportDateFormatter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

@Service
public class DocumentComparisonExportService {

  private static final String DOCX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private static final String REMOVED_COLOR = "C00000";
  private static final String ADDED_COLOR = "1F7A3D";
  private static final String MUTED_COLOR = "808080";
  private static final int FILE_NAME_MAX_LENGTH = 80;

  private final DocumentComparisonService comparisonService;

  public DocumentComparisonExportService(DocumentComparisonService comparisonService) {
    this.comparisonService = comparisonService;
  }

  public ComparisonExportFile exportDocx(UUID comparisonId, UUID lawyerId, List<UUID> orgIds) {
    DocumentComparisonDto comparison = comparisonService.get(comparisonId, lawyerId, orgIds);
    return new ComparisonExportFile(toDocx(comparison), fileName(comparison), DOCX_CONTENT_TYPE);
  }

  private byte[] toDocx(DocumentComparisonDto comparison) {
    try (XWPFDocument document = new XWPFDocument();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

      title(document, "Редлайн: сравнение версий");
      writeHeader(document, comparison);
      writeLegend(document);
      writeChanges(document, comparison.changes());

      document.write(outputStream);
      return outputStream.toByteArray();
    } catch (IOException ex) {
      throw new CaseExportException("Failed to generate redline .docx export", ex);
    }
  }

  private void writeHeader(XWPFDocument document, DocumentComparisonDto comparison) {
    XWPFParagraph paragraph = document.createParagraph();
    paragraph.setAlignment(ParagraphAlignment.CENTER);
    coloredRun(paragraph, comparison.baseDocumentTitle(), REMOVED_COLOR, 12, true);
    plainRun(paragraph, "  →  ", 12);
    coloredRun(paragraph, comparison.revisedDocumentTitle(), ADDED_COLOR, 12, true);

    mutedLine(
        document,
        "Сформировано: "
            + ExportDateFormatter.format(comparison.createdAt())
            + "  ·  Изменений: "
            + comparison.changeCount()
            + "  ·  Риск: "
            + comparison.riskScore()
            + "/100"
            + "  ·  Высокий риск: "
            + comparison.highRiskCount());

    sectionHeading(document, "Резюме");
    bodyParagraph(document, comparison.summary());
  }

  private void writeLegend(XWPFDocument document) {
    XWPFParagraph paragraph = document.createParagraph();
    XWPFRun removedRun = coloredRun(paragraph, "Удалено", REMOVED_COLOR, 10, false);
    removedRun.setStrikeThrough(true);
    plainRun(paragraph, "   ", 10);
    XWPFRun addedRun = coloredRun(paragraph, "Добавлено", ADDED_COLOR, 10, false);
    addedRun.setUnderline(UnderlinePatterns.SINGLE);
  }

  private void writeChanges(XWPFDocument document, List<DiffChange> changes) {
    sectionHeading(document, "Изменения (" + changes.size() + ")");
    if (changes.isEmpty()) {
      mutedLine(document, "Различий между версиями не обнаружено.");
      return;
    }
    for (DiffChange change : changes) {
      writeChange(document, change);
    }
  }

  private void writeChange(XWPFDocument document, DiffChange change) {
    subHeading(document, change.order() + ". " + typeLabel(change.type()) + riskSuffix(change));
    if (change.type() == DiffChangeType.MODIFIED && !change.segments().isEmpty()) {
      writeInlineSegments(document, change.segments());
    } else {
      writeBlockTexts(document, change);
    }
    if (change.comment() != null && !change.comment().isBlank()) {
      mutedLine(document, "Комментарий: " + change.comment());
    }
  }

  private void writeInlineSegments(XWPFDocument document, List<DiffSegment> segments) {
    XWPFParagraph paragraph = document.createParagraph();
    for (DiffSegment segment : segments) {
      switch (segment.type()) {
        case EQUAL -> plainRun(paragraph, segment.text(), 11);
        case REMOVED -> {
          XWPFRun run = coloredRun(paragraph, segment.text(), REMOVED_COLOR, 11, false);
          run.setStrikeThrough(true);
        }
        case ADDED -> {
          XWPFRun run = coloredRun(paragraph, segment.text(), ADDED_COLOR, 11, false);
          run.setUnderline(UnderlinePatterns.SINGLE);
        }
      }
    }
  }

  private void writeBlockTexts(XWPFDocument document, DiffChange change) {
    if (change.baseText() != null && !change.baseText().isBlank()) {
      for (String line : splitLines(change.baseText())) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = coloredRun(paragraph, line, REMOVED_COLOR, 11, false);
        run.setStrikeThrough(true);
      }
    }
    if (change.revisedText() != null && !change.revisedText().isBlank()) {
      for (String line : splitLines(change.revisedText())) {
        XWPFParagraph paragraph = document.createParagraph();
        coloredRun(paragraph, line, ADDED_COLOR, 11, false);
      }
    }
  }

  private String typeLabel(DiffChangeType type) {
    return switch (type) {
      case ADDED -> "Добавлено";
      case REMOVED -> "Удалено";
      case MODIFIED -> "Изменено";
    };
  }

  private String riskSuffix(DiffChange change) {
    return change.riskLevel() == null ? "" : " · риск: " + change.riskLevel().displayName();
  }

  private String[] splitLines(String text) {
    return text.split("\\r?\\n");
  }

  private void title(XWPFDocument document, String text) {
    XWPFParagraph paragraph = document.createParagraph();
    paragraph.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = paragraph.createRun();
    run.setText(text);
    run.setBold(true);
    run.setFontSize(18);
  }

  private void sectionHeading(XWPFDocument document, String text) {
    XWPFParagraph paragraph = document.createParagraph();
    paragraph.setSpacingBefore(240);
    XWPFRun run = paragraph.createRun();
    run.setText(text);
    run.setBold(true);
    run.setFontSize(14);
  }

  private void subHeading(XWPFDocument document, String text) {
    XWPFParagraph paragraph = document.createParagraph();
    paragraph.setSpacingBefore(160);
    XWPFRun run = paragraph.createRun();
    run.setText(text);
    run.setBold(true);
    run.setFontSize(12);
  }

  private void bodyParagraph(XWPFDocument document, String text) {
    for (String line : splitLines(text)) {
      XWPFParagraph paragraph = document.createParagraph();
      plainRun(paragraph, line, 11);
    }
  }

  private void mutedLine(XWPFDocument document, String text) {
    XWPFParagraph paragraph = document.createParagraph();
    coloredRun(paragraph, text, MUTED_COLOR, 10, false);
  }

  private XWPFRun plainRun(XWPFParagraph paragraph, String text, int fontSize) {
    XWPFRun run = paragraph.createRun();
    String[] lines = splitLines(text);
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        run.addBreak();
      }
      run.setText(lines[i], i);
    }
    run.setFontSize(fontSize);
    return run;
  }

  private XWPFRun coloredRun(
      XWPFParagraph paragraph, String text, String color, int fontSize, boolean bold) {
    XWPFRun run = plainRun(paragraph, text, fontSize);
    run.setColor(color);
    run.setBold(bold);
    return run;
  }

  private String fileName(DocumentComparisonDto comparison) {
    String base =
        (comparison.baseDocumentTitle() + "-" + comparison.revisedDocumentTitle())
            .replaceAll("[\\\\/:*?\"<>|]", "_")
            .strip();
    if (base.length() > FILE_NAME_MAX_LENGTH) {
      base = base.substring(0, FILE_NAME_MAX_LENGTH).strip();
    }
    return "redline-" + (base.isEmpty() ? comparison.id().toString() : base) + ".docx";
  }
}
