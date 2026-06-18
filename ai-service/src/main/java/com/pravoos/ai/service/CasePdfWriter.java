package com.pravoos.ai.service;

import com.pravoos.ai.exception.CaseExportException;
import com.pravoos.ai.model.dto.CaseExportModel;
import com.pravoos.ai.model.dto.CaseExportModel.ClientSection;
import com.pravoos.ai.model.dto.CaseExportModel.DocumentSection;
import com.pravoos.ai.model.dto.CaseExportModel.DraftSection;
import com.pravoos.ai.model.dto.CaseExportModel.ResponseSection;
import com.pravoos.ai.util.ExportDateFormatter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class CasePdfWriter {

    private static final String REGULAR_FONT = "/fonts/DejaVuSans.ttf";
    private static final String BOLD_FONT = "/fonts/DejaVuSans-Bold.ttf";

    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    public byte[] write(CaseExportModel model) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PDType0Font regular = loadFont(document, REGULAR_FONT);
            PDType0Font bold = loadFont(document, BOLD_FONT);
            Renderer renderer = new Renderer(document, regular, bold);

            renderer.title(model.title());
            renderer.muted("Дело создано: " + ExportDateFormatter.format(model.createdAt()));
            if (isPresent(model.description())) {
                renderer.body(model.description());
            }

            renderClient(renderer, model.client());
            renderDocuments(renderer, model.documents());
            renderResponses(renderer, model.responses());
            renderDrafts(renderer, model.drafts());

            renderer.finish();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new CaseExportException("Failed to generate .pdf export", ex);
        }
    }

    private void renderClient(Renderer renderer, ClientSection client) throws IOException {
        if (client == null) {
            return;
        }
        renderer.heading("Клиент");
        renderer.labeled("Имя / название", client.name());
        renderer.labeled("Тип", client.type());
        renderer.labeled("Телефон", client.phone());
        renderer.labeled("Email", client.email());
        renderer.labeled("ИНН", client.inn());
        if (isPresent(client.notes())) {
            renderer.labeled("Заметки", client.notes());
        }
    }

    private void renderDocuments(Renderer renderer, List<DocumentSection> documents) throws IOException {
        renderer.heading("Документы дела (" + documents.size() + ")");
        if (documents.isEmpty()) {
            renderer.muted("Документы не загружены.");
            return;
        }
        for (DocumentSection doc : documents) {
            renderer.bullet(doc.title() + " — " + doc.fileName() + " [" + doc.status() + "]");
        }
    }

    private void renderResponses(Renderer renderer, List<ResponseSection> responses) throws IOException {
        renderer.heading("Заключения AI (" + responses.size() + ")");
        if (responses.isEmpty()) {
            renderer.muted("Анализ ещё не запускался.");
            return;
        }
        for (ResponseSection response : responses) {
            renderer.subHeading(response.workflowName() + " · " + ExportDateFormatter.format(response.createdAt()));
            if (isPresent(response.query())) {
                renderer.body("Запрос: " + response.query());
            }
            renderer.body(response.result());
            if (!response.sources().isEmpty()) {
                renderer.muted("Источники: " + String.join("; ", response.sources()));
            }
        }
    }

    private void renderDrafts(Renderer renderer, List<DraftSection> drafts) throws IOException {
        renderer.heading("Черновики документов (" + drafts.size() + ")");
        if (drafts.isEmpty()) {
            renderer.muted("Черновики не создавались.");
            return;
        }
        for (DraftSection draft : drafts) {
            renderer.subHeading(draft.typeName() + " · " + ExportDateFormatter.format(draft.createdAt()));
            renderer.body(draft.content());
        }
    }

    private PDType0Font loadFont(PDDocument document, String resourcePath) throws IOException {
        try (InputStream fontStream = getClass().getResourceAsStream(resourcePath)) {
            if (fontStream == null) {
                throw new IOException("Embedded font not found on classpath: " + resourcePath);
            }
            return PDType0Font.load(document, fontStream);
        }
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static final class Renderer {

        private final PDDocument document;
        private final PDType0Font regular;
        private final PDType0Font bold;
        private PDPage page;
        private PDPageContentStream content;
        private float cursorY;

        private Renderer(PDDocument document, PDType0Font regular, PDType0Font bold) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            newPage();
        }

        void title(String text) throws IOException {
            String safe = sanitize(bold, text);
            float size = 18f;
            float width = textWidth(bold, size, safe);
            float x = Math.max(MARGIN, (PAGE_WIDTH - width) / 2f);
            float leading = size * 1.5f;
            ensureSpace(leading);
            drawLine(safe, bold, size, x, Color.BLACK);
            cursorY -= leading;
        }

        void heading(String text) throws IOException {
            spacer(14f);
            paragraph(text, bold, 14f, 0f, Color.BLACK);
        }

        void subHeading(String text) throws IOException {
            spacer(8f);
            paragraph(text, bold, 12f, 0f, Color.BLACK);
        }

        void body(String text) throws IOException {
            paragraph(text, regular, 11f, 0f, Color.BLACK);
        }

        void bullet(String text) throws IOException {
            paragraph("• " + text, regular, 11f, 0f, Color.BLACK);
        }

        void muted(String text) throws IOException {
            paragraph(text, regular, 10f, 0f, Color.GRAY);
        }

        void labeled(String label, String value) throws IOException {
            paragraph(label + ": " + (isBlank(value) ? "—" : value), regular, 11f, 0f, Color.BLACK);
        }

        private void paragraph(String text, PDType0Font font, float size, float indent, Color color) throws IOException {
            float leading = size * 1.35f;
            String normalized = text.replace("\t", "    ");
            for (String logicalLine : normalized.split("\\r?\\n", -1)) {
                String safe = sanitize(font, logicalLine);
                if (safe.isEmpty()) {
                    spacer(leading);
                    continue;
                }
                for (String visualLine : wrap(font, size, safe, CONTENT_WIDTH - indent)) {
                    ensureSpace(leading);
                    drawLine(visualLine, font, size, MARGIN + indent, color);
                    cursorY -= leading;
                }
            }
        }

        private void drawLine(String text, PDType0Font font, float size, float x, Color color) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.setNonStrokingColor(color);
            content.newLineAtOffset(x, cursorY);
            content.showText(text);
            content.endText();
        }

        private void spacer(float height) throws IOException {
            if (cursorY - height < MARGIN) {
                newPage();
            } else {
                cursorY -= height;
            }
        }

        private void ensureSpace(float needed) throws IOException {
            if (cursorY - needed < MARGIN) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            cursorY = PAGE_HEIGHT - MARGIN;
        }

        void finish() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }

        private List<String> wrap(PDType0Font font, float size, String text, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split(" ", -1)) {
                if (current.length() == 0) {
                    appendWord(lines, current, word, font, size, maxWidth);
                } else if (textWidth(font, size, current + " " + word) <= maxWidth) {
                    current.append(' ').append(word);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    appendWord(lines, current, word, font, size, maxWidth);
                }
            }
            lines.add(current.toString());
            return lines;
        }

        private void appendWord(List<String> lines, StringBuilder current, String word,
                                PDType0Font font, float size, float maxWidth) throws IOException {
            if (textWidth(font, size, word) <= maxWidth) {
                current.append(word);
                return;
            }
            StringBuilder piece = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                char symbol = word.charAt(i);
                if (piece.length() > 0 && textWidth(font, size, piece.toString() + symbol) > maxWidth) {
                    lines.add(piece.toString());
                    piece.setLength(0);
                }
                piece.append(symbol);
            }
            current.append(piece);
        }

        private float textWidth(PDType0Font font, float size, String text) throws IOException {
            return font.getStringWidth(text) / 1000f * size;
        }

        private String sanitize(PDType0Font font, String text) {
            StringBuilder builder = new StringBuilder();
            int index = 0;
            while (index < text.length()) {
                int codePoint = text.codePointAt(index);
                int charCount = Character.charCount(codePoint);
                String symbol = text.substring(index, index + charCount);
                if (codePoint >= 32) {
                    try {
                        font.getStringWidth(symbol);
                        builder.append(symbol);
                    } catch (IllegalArgumentException | IOException ex) {
                        builder.append(' ');
                    }
                }
                index += charCount;
            }
            return builder.toString();
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
