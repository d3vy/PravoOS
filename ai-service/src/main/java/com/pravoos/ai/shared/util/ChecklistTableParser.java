package com.pravoos.ai.shared.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChecklistTableParser {

  private static final int MAX_TASK_TEXT_LENGTH = 1000;
  private static final String TASK_PREFIX = "Подготовить документ: ";

  private ChecklistTableParser() {}

  public static List<String> extractMissingDocumentTasks(String checklistMarkdown) {
    List<String> tasks = new ArrayList<>();
    if (checklistMarkdown == null || checklistMarkdown.isBlank()) {
      return tasks;
    }

    for (String line : checklistMarkdown.split("\\r?\\n")) {
      List<String> cells = splitRow(line);
      if (cells.size() < 2 || isHeaderRow(cells) || isSeparatorRow(cells)) {
        continue;
      }

      String document = cells.get(0);
      String status = cells.get(1);
      String note = cells.size() > 2 ? cells.get(2) : "";
      if (document.isBlank() || !indicatesMissing(status)) {
        continue;
      }

      tasks.add(buildTaskText(document, note));
    }
    return tasks;
  }

  private static List<String> splitRow(String line) {
    String trimmed = line.strip();
    if (!trimmed.contains("|")) {
      return List.of();
    }
    if (trimmed.startsWith("|")) {
      trimmed = trimmed.substring(1);
    }
    if (trimmed.endsWith("|")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }

    List<String> cells = new ArrayList<>();
    for (String cell : trimmed.split("\\|")) {
      cells.add(cell.strip());
    }
    return cells;
  }

  private static boolean isHeaderRow(List<String> cells) {
    String first = cells.get(0).toLowerCase(Locale.ROOT);
    String second = cells.get(1).toLowerCase(Locale.ROOT);
    return first.contains("документ") && second.contains("статус");
  }

  private static boolean isSeparatorRow(List<String> cells) {
    return cells.stream()
        .allMatch(cell -> cell.chars().allMatch(c -> c == '-' || c == ':' || c == ' '));
  }

  private static boolean indicatesMissing(String status) {
    String normalized = status.toLowerCase(Locale.ROOT);
    return normalized.contains("отсут") || normalized.contains("частичн");
  }

  private static String buildTaskText(String document, String note) {
    String text =
        note.isBlank() ? TASK_PREFIX + document : TASK_PREFIX + document + " (" + note + ")";
    return text.length() <= MAX_TASK_TEXT_LENGTH ? text : text.substring(0, MAX_TASK_TEXT_LENGTH);
  }
}
