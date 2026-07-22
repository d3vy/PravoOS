package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.internal.dto.ReviewExportFile;
import com.pravoos.ai.core.internal.model.entity.TabularReview;
import com.pravoos.ai.core.internal.model.entity.TabularReviewCell;
import com.pravoos.ai.core.internal.model.entity.TabularReviewDocument;
import com.pravoos.ai.shared.exception.InvalidExportFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TabularReviewExportService {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCUMENT_COLUMN_HEADER = "Документ";
    private static final String EMPTY_CELL = "—";
    private static final int DOCUMENT_COLUMN_WIDTH = 12000;
    private static final int ANSWER_COLUMN_WIDTH = 14000;

    private final TabularReviewService reviewService;

    public TabularReviewExportService(TabularReviewService reviewService) {
        this.reviewService = reviewService;
    }

    public ReviewExportFile export(UUID reviewId, String format, UUID lawyerId, List<UUID> orgIds) {
        TabularReview review = reviewService.requireVisibleReview(reviewId, lawyerId, orgIds);
        List<TabularReviewDocument> documents = reviewService.documentsOf(reviewId);
        Map<String, TabularReviewCell> cellIndex = indexCells(reviewService.cellsOf(reviewId));

        String normalized = format == null || format.isBlank() ? "xlsx" : format.trim().toLowerCase();
        return switch (normalized) {
            case "xlsx" -> new ReviewExportFile(toXlsx(review, documents, cellIndex),
                    fileName(review, "xlsx"), XLSX_CONTENT_TYPE);
            case "docx" -> new ReviewExportFile(toDocx(review, documents, cellIndex),
                    fileName(review, "docx"), DOCX_CONTENT_TYPE);
            default -> throw new InvalidExportFormatException(format);
        };
    }

    private byte[] toXlsx(TabularReview review, List<TabularReviewDocument> documents,
                          Map<String, TabularReviewCell> cellIndex) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Разбор");
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle bodyStyle = bodyStyle(workbook);

            Row headerRow = sheet.createRow(0);
            writeCell(headerRow, 0, DOCUMENT_COLUMN_HEADER, headerStyle);
            List<String> questions = review.getQuestions();
            for (int column = 0; column < questions.size(); column++) {
                writeCell(headerRow, column + 1, questions.get(column), headerStyle);
            }

            for (int rowIndex = 0; rowIndex < documents.size(); rowIndex++) {
                TabularReviewDocument document = documents.get(rowIndex);
                Row row = sheet.createRow(rowIndex + 1);
                writeCell(row, 0, document.getDocumentTitle(), bodyStyle);
                for (int column = 0; column < questions.size(); column++) {
                    TabularReviewCell cell = cellIndex.get(key(document.getDocumentId(), column));
                    writeCell(row, column + 1, cell != null ? cell.getAnswer() : EMPTY_CELL, bodyStyle);
                }
            }

            sheet.setColumnWidth(0, DOCUMENT_COLUMN_WIDTH);
            for (int column = 0; column < questions.size(); column++) {
                sheet.setColumnWidth(column + 1, ANSWER_COLUMN_WIDTH);
            }
            sheet.createFreezePane(1, 1);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate tabular review .xlsx", e);
        }
    }

    private byte[] toDocx(TabularReview review, List<TabularReviewDocument> documents,
                          Map<String, TabularReviewCell> cellIndex) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(review.getTitle());
            titleRun.setBold(true);
            titleRun.setFontSize(16);

            List<String> questions = review.getQuestions();
            XWPFTable table = document.createTable(documents.size() + 1, questions.size() + 1);

            XWPFTableRow headerRow = table.getRow(0);
            setCellText(headerRow.getCell(0), DOCUMENT_COLUMN_HEADER, true);
            for (int column = 0; column < questions.size(); column++) {
                setCellText(headerRow.getCell(column + 1), questions.get(column), true);
            }

            for (int rowIndex = 0; rowIndex < documents.size(); rowIndex++) {
                TabularReviewDocument reviewDocument = documents.get(rowIndex);
                XWPFTableRow row = table.getRow(rowIndex + 1);
                setCellText(row.getCell(0), reviewDocument.getDocumentTitle(), false);
                for (int column = 0; column < questions.size(); column++) {
                    TabularReviewCell cell = cellIndex.get(key(reviewDocument.getDocumentId(), column));
                    setCellText(row.getCell(column + 1), cell != null ? cell.getAnswer() : EMPTY_CELL, false);
                }
            }

            document.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate tabular review .docx", e);
        }
    }

    private Map<String, TabularReviewCell> indexCells(List<TabularReviewCell> cells) {
        Map<String, TabularReviewCell> index = new HashMap<>();
        for (TabularReviewCell cell : cells) {
            index.put(key(cell.getDocumentId(), cell.getQuestionIndex()), cell);
        }
        return index;
    }

    private String key(UUID documentId, int questionIndex) {
        return documentId + ":" + questionIndex;
    }

    private void writeCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle bodyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private void setCellText(XWPFTableCell tableCell, String text, boolean bold) {
        XWPFParagraph paragraph = tableCell.getParagraphs().get(0);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontSize(10);
    }

    private String fileName(TabularReview review, String extension) {
        String base = review.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        return (base.isEmpty() ? "review" : base) + "." + extension;
    }
}
