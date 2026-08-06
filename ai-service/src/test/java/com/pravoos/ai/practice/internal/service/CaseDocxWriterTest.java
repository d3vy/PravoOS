package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.practice.internal.dto.CaseExportModel;
import com.pravoos.ai.practice.internal.dto.CaseExportModel.ClientSection;
import com.pravoos.ai.practice.internal.dto.CaseExportModel.DocumentSection;
import com.pravoos.ai.practice.internal.dto.CaseExportModel.DraftSection;
import com.pravoos.ai.practice.internal.dto.CaseExportModel.ResponseSection;
import com.pravoos.ai.practice.internal.dto.CaseExportModel.TaskSection;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class CaseDocxWriterTest {

  private final CaseDocxWriter writer = new CaseDocxWriter();

  private String extractText(byte[] bytes) throws IOException {
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
      return extractor.getText();
    }
  }

  private CaseExportModel emptyModel(ClientSection client) {
    return new CaseExportModel(
        "Дело №1",
        null,
        "В работе",
        LocalDateTime.of(2026, 1, 1, 9, 0),
        client,
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  @Test
  void writesMutedFallbackLinesForEmptyCollections() throws IOException {
    byte[] bytes = writer.write(emptyModel(null));

    String text = extractText(bytes);
    assertThat(text).contains("Дело №1");
    assertThat(text).contains("Документы не загружены.");
    assertThat(text).contains("Задачи не добавлены.");
    assertThat(text).contains("Анализ ещё не запускался.");
    assertThat(text).contains("Черновики не создавались.");
    assertThat(text).doesNotContain("Клиент");
  }

  @Test
  void writesClientSectionWithDashForMissingValues() throws IOException {
    ClientSection client = new ClientSection("Иванов И.И.", "Физлицо", null, null, null, null);
    byte[] bytes = writer.write(emptyModel(client));

    String text = extractText(bytes);
    assertThat(text).contains("Клиент");
    assertThat(text).contains("Иванов И.И.");
    assertThat(text).contains("Телефон: —");
    assertThat(text).doesNotContain("Заметки");
  }

  @Test
  void includesClientNotesOnlyWhenPresent() throws IOException {
    ClientSection client =
        new ClientSection("ООО Ромашка", "Юрлицо", "+7999", "a@b.com", "770123456", "VIP клиент");
    byte[] bytes = writer.write(emptyModel(client));

    String text = extractText(bytes);
    assertThat(text).contains("Заметки: VIP клиент");
  }

  @Test
  void writesDocumentsTasksResponsesAndDrafts() throws IOException {
    CaseExportModel model =
        new CaseExportModel(
            "Дело №2",
            "Описание дела",
            "Приём",
            LocalDateTime.of(2026, 1, 1, 9, 0),
            null,
            List.of(
                new DocumentSection(
                    "Договор", "contract.pdf", "Обработан", LocalDateTime.of(2026, 1, 1, 9, 0))),
            List.of(
                new TaskSection("Открытая задача", false, LocalDate.of(2026, 2, 1)),
                new TaskSection("Закрытая задача", true, null)),
            List.of(
                new ResponseSection(
                    "Проверка документов",
                    "Есть ли риски?",
                    "Риски отсутствуют.",
                    List.of("Источник 1"),
                    LocalDateTime.of(2026, 1, 2, 12, 0))),
            List.of(
                new DraftSection(
                    "Жалоба",
                    "Черновик жалобы",
                    "Текст черновика",
                    LocalDateTime.of(2026, 1, 3, 8, 0))));

    byte[] bytes = writer.write(model);
    String text = extractText(bytes);

    assertThat(text).contains("Описание дела");
    assertThat(text).contains("Документы дела (1)");
    assertThat(text).contains("Договор — contract.pdf [Обработан]");
    assertThat(text).contains("Задачи по делу (1 из 2 активны)");
    assertThat(text).contains("[ ] Открытая задача — до 01.02.2026");
    assertThat(text).contains("[x] Закрытая задача");
    assertThat(text).contains("Заключения AI (1)");
    assertThat(text).contains("Запрос: Есть ли риски?");
    assertThat(text).contains("Риски отсутствуют.");
    assertThat(text).contains("Источники: Источник 1");
    assertThat(text).contains("Черновики документов (1)");
    assertThat(text).contains("Текст черновика");
  }
}
