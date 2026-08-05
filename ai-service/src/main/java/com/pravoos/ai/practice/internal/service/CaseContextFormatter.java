package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseHearingEvent;
import com.pravoos.ai.practice.internal.model.entity.CaseParty;
import com.pravoos.ai.practice.internal.model.entity.CaseTask;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class CaseContextFormatter {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final int MAX_TIMELINE_EVENTS = 60;
  private static final int MAX_CHECKLIST_TASKS = 40;
  private static final int EVENT_DESCRIPTION_MAX_LENGTH = 300;

  private CaseContextFormatter() {}

  static String formatCaseCard(Case caseEntity, List<CaseParty> parties) {
    StringBuilder builder = new StringBuilder();
    builder.append("Название: ").append(caseEntity.getTitle()).append('\n');
    builder.append("Статус: ").append(caseEntity.getStatus().getDisplayName()).append('\n');
    if (caseEntity.getCourtCaseNumber() != null) {
      builder
          .append("Номер дела (")
          .append(caseEntity.getCourtSystem().getDisplayName())
          .append("): ")
          .append(caseEntity.getCourtCaseNumber())
          .append('\n');
    }
    if (caseEntity.getJudgeName() != null) {
      builder.append("Судья: ").append(caseEntity.getJudgeName()).append('\n');
    }
    if (!parties.isEmpty()) {
      builder.append("Стороны: ");
      builder.append(
          parties.stream()
              .map(
                  party ->
                      party.getRole() == null
                          ? party.getName()
                          : party.getName() + " (" + party.getRole() + ")")
              .reduce((first, second) -> first + "; " + second)
              .orElse(""));
      builder.append('\n');
    }
    if (caseEntity.getNextHearingDate() != null) {
      builder
          .append("Ближайшее заседание: ")
          .append(DATE_FORMATTER.format(caseEntity.getNextHearingDate()))
          .append('\n');
    }
    if (caseEntity.getDescription() != null && !caseEntity.getDescription().isBlank()) {
      builder.append("Описание: ").append(caseEntity.getDescription());
    }
    return builder.toString().strip();
  }

  static String formatTimeline(List<CaseHearingEvent> events) {
    if (events.isEmpty()) {
      return "Событий по делу из КАД.Арбитр пока нет.";
    }
    StringBuilder builder = new StringBuilder();
    events.stream()
        .limit(MAX_TIMELINE_EVENTS)
        .forEach(
            event -> {
              builder.append(
                  event.getEventDate() == null ? "—" : DATE_FORMATTER.format(event.getEventDate()));
              builder
                  .append(" — ")
                  .append(event.getEventType() == null ? "событие" : event.getEventType());
              if (event.getCourtName() != null && !event.getCourtName().isBlank()) {
                builder.append(" (").append(event.getCourtName()).append(')');
              }
              if (event.getDescription() != null && !event.getDescription().isBlank()) {
                builder.append(": ").append(truncate(event.getDescription()));
              }
              builder.append('\n');
            });
    return builder.toString().strip();
  }

  static String formatChecklist(List<CaseTask> tasks) {
    if (tasks.isEmpty()) {
      return "Задач по делу пока нет.";
    }
    StringBuilder builder = new StringBuilder();
    tasks.stream()
        .limit(MAX_CHECKLIST_TASKS)
        .forEach(
            task -> {
              builder.append(task.isDone() ? "[выполнено] " : "[в работе] ").append(task.getText());
              if (task.getDueDate() != null) {
                builder
                    .append(" (срок ")
                    .append(DATE_FORMATTER.format(task.getDueDate()))
                    .append(')');
              }
              builder.append('\n');
            });
    return builder.toString().strip();
  }

  private static String truncate(String text) {
    String trimmed = text.trim();
    return trimmed.length() <= EVENT_DESCRIPTION_MAX_LENGTH
        ? trimmed
        : trimmed.substring(0, EVENT_DESCRIPTION_MAX_LENGTH) + "...";
  }
}
