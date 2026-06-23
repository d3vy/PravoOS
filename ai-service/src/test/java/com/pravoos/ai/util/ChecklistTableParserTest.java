package com.pravoos.ai.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChecklistTableParserTest {

    @Test
    void extractsTasksOnlyForMissingOrPartialDocuments() {
        String markdown = """
                | Документ | Статус | Примечание |
                | --- | --- | --- |
                | Паспорт | Есть | |
                | Договор аренды | Отсутствует | нужен оригинал |
                | Выписка ЕГРН | Частично | только копия |
                """;

        List<String> tasks = ChecklistTableParser.extractMissingDocumentTasks(markdown);

        assertThat(tasks).containsExactly(
                "Подготовить документ: Договор аренды (нужен оригинал)",
                "Подготовить документ: Выписка ЕГРН (только копия)");
    }

    @Test
    void omitsNoteWhenAbsent() {
        String markdown = """
                | Документ | Статус |
                | --- | --- |
                | Доверенность | Отсутствует |
                """;

        assertThat(ChecklistTableParser.extractMissingDocumentTasks(markdown))
                .containsExactly("Подготовить документ: Доверенность");
    }

    @Test
    void nonTableInputReturnsEmptyList() {
        assertThat(ChecklistTableParser.extractMissingDocumentTasks("просто текст без таблицы")).isEmpty();
    }

    @Test
    void blankInputReturnsEmptyList() {
        assertThat(ChecklistTableParser.extractMissingDocumentTasks("  ")).isEmpty();
        assertThat(ChecklistTableParser.extractMissingDocumentTasks(null)).isEmpty();
    }
}
