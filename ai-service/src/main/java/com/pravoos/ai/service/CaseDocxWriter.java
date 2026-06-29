package com.pravoos.ai.service;

import com.pravoos.ai.exception.CaseExportException;
import com.pravoos.ai.model.dto.CaseExportModel;
import com.pravoos.ai.model.dto.CaseExportModel.*;
import com.pravoos.ai.util.ExportDateFormatter;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class CaseDocxWriter {

    public byte[] write(CaseExportModel model) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            title(document, model.title());
            mutedLine(document, "Статус: " + model.status() + "  ·  Создано: " + ExportDateFormatter.format(model.createdAt()));
            if (isPresent(model.description())) {
                bodyParagraph(document, model.description());
            }

            writeClient(document, model.client());
            writeDocuments(document, model.documents());
            writeTasks(document, model.tasks());
            writeResponses(document, model.responses());
            writeDrafts(document, model.drafts());

            document.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new CaseExportException("Failed to generate .docx export", ex);
        }
    }

    private void writeClient(XWPFDocument document, ClientSection client) {
        if (client == null) {
            return;
        }
        sectionHeading(document, "Клиент");
        labeledLine(document, "Имя / название", client.name());
        labeledLine(document, "Тип", client.type());
        labeledLine(document, "Телефон", client.phone());
        labeledLine(document, "Email", client.email());
        labeledLine(document, "ИНН", client.inn());
        if (isPresent(client.notes())) {
            labeledLine(document, "Заметки", client.notes());
        }
    }

    private void writeDocuments(XWPFDocument document, java.util.List<DocumentSection> documents) {
        sectionHeading(document, "Документы дела (" + documents.size() + ")");
        if (documents.isEmpty()) {
            mutedLine(document, "Документы не загружены.");
            return;
        }
        for (DocumentSection doc : documents) {
            bulletLine(document, doc.title() + " — " + doc.fileName() + " [" + doc.status() + "]");
        }
    }

    private void writeTasks(XWPFDocument document, java.util.List<TaskSection> tasks) {
        long openCount = tasks.stream().filter(task -> !task.done()).count();
        sectionHeading(document, "Задачи по делу (" + openCount + " из " + tasks.size() + " активны)");
        if (tasks.isEmpty()) {
            mutedLine(document, "Задачи не добавлены.");
            return;
        }
        for (TaskSection task : tasks) {
            bulletLine(document, taskLabel(task));
        }
    }

    private String taskLabel(TaskSection task) {
        String checkbox = task.done() ? "[x] " : "[ ] ";
        String dueDate = ExportDateFormatter.formatDate(task.dueDate());
        return checkbox + task.text() + (dueDate == null ? "" : " — до " + dueDate);
    }

    private void writeResponses(XWPFDocument document, java.util.List<ResponseSection> responses) {
        sectionHeading(document, "Заключения AI (" + responses.size() + ")");
        if (responses.isEmpty()) {
            mutedLine(document, "Анализ ещё не запускался.");
            return;
        }
        for (ResponseSection response : responses) {
            subHeading(document, response.workflowName() + " · " + ExportDateFormatter.format(response.createdAt()));
            if (isPresent(response.query())) {
                bodyParagraph(document, "Запрос: " + response.query());
            }
            bodyParagraph(document, response.result());
            if (!response.sources().isEmpty()) {
                mutedLine(document, "Источники: " + String.join("; ", response.sources()));
            }
        }
    }

    private void writeDrafts(XWPFDocument document, java.util.List<DraftSection> drafts) {
        sectionHeading(document, "Черновики документов (" + drafts.size() + ")");
        if (drafts.isEmpty()) {
            mutedLine(document, "Черновики не создавались.");
            return;
        }
        for (DraftSection draft : drafts) {
            subHeading(document, draft.typeName() + " · " + ExportDateFormatter.format(draft.createdAt()));
            bodyParagraph(document, draft.content());
        }
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

    private void labeledLine(XWPFDocument document, String label, String value) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun labelRun = paragraph.createRun();
        labelRun.setText(label + ": ");
        labelRun.setBold(true);
        labelRun.setFontSize(11);
        XWPFRun valueRun = paragraph.createRun();
        valueRun.setText(isPresent(value) ? value : "—");
        valueRun.setFontSize(11);
    }

    private void bulletLine(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText("• " + text);
        run.setFontSize(11);
    }

    private void bodyParagraph(XWPFDocument document, String text) {
        for (String line : text.split("\\r?\\n")) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(line);
            run.setFontSize(11);
        }
    }

    private void mutedLine(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(10);
        run.setColor("808080");
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
