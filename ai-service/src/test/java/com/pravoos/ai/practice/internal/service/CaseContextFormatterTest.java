package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseHearingEvent;
import com.pravoos.ai.practice.internal.model.entity.CaseParty;
import com.pravoos.ai.practice.internal.model.entity.CaseTask;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaseContextFormatterTest {

  @Test
  void formatCaseCardIncludesTitleStatusAndOptionalFields() {
    Case caseEntity = new Case();
    caseEntity.setTitle("Иванов против ООО Ромашка");
    caseEntity.setStatus(CaseStatus.IN_PROGRESS);
    caseEntity.setCourtSystem(CourtSystem.ARBITR);
    caseEntity.setCourtCaseNumber("A40-12345/2026");
    caseEntity.setJudgeName("Петрова А.А.");
    caseEntity.setNextHearingDate(LocalDate.of(2026, 9, 1));
    caseEntity.setDescription("Спор о взыскании задолженности");

    List<CaseParty> parties =
        List.of(
            new CaseParty(UUID.randomUUID(), "Иванов И.И.", "Истец"),
            new CaseParty(UUID.randomUUID(), "ООО Ромашка", null));

    String card = CaseContextFormatter.formatCaseCard(caseEntity, parties);

    assertThat(card).contains("Название: Иванов против ООО Ромашка");
    assertThat(card).contains("Статус: В работе");
    assertThat(card).contains("Номер дела (КАД.Арбитр): A40-12345/2026");
    assertThat(card).contains("Судья: Петрова А.А.");
    assertThat(card).contains("Стороны: Иванов И.И. (Истец); ООО Ромашка");
    assertThat(card).contains("Ближайшее заседание: 01.09.2026");
    assertThat(card).contains("Описание: Спор о взыскании задолженности");
  }

  @Test
  void formatCaseCardOmitsOptionalFieldsWhenAbsent() {
    Case caseEntity = new Case();
    caseEntity.setTitle("Дело без деталей");
    caseEntity.setStatus(CaseStatus.INTAKE);

    String card = CaseContextFormatter.formatCaseCard(caseEntity, List.of());

    assertThat(card).doesNotContain("Номер дела");
    assertThat(card).doesNotContain("Судья");
    assertThat(card).doesNotContain("Стороны");
    assertThat(card).doesNotContain("Ближайшее заседание");
    assertThat(card).doesNotContain("Описание");
  }

  @Test
  void formatTimelineReturnsPlaceholderWhenNoEvents() {
    assertThat(CaseContextFormatter.formatTimeline(List.of()))
        .isEqualTo("Событий по делу из КАД.Арбитр пока нет.");
  }

  @Test
  void formatTimelineFormatsEventsWithDateTypeAndCourt() {
    CaseHearingEvent event =
        new CaseHearingEvent(
            UUID.randomUUID(),
            "src-1",
            LocalDate.of(2026, 3, 5),
            "Судебное заседание",
            "Рассмотрение по существу",
            "Арбитражный суд г. Москвы");

    String timeline = CaseContextFormatter.formatTimeline(List.of(event));

    assertThat(timeline)
        .isEqualTo(
            "05.03.2026 — Судебное заседание (Арбитражный суд г. Москвы): Рассмотрение по существу");
  }

  @Test
  void formatTimelineTruncatesLongDescriptions() {
    String longDescription = "а".repeat(400);
    CaseHearingEvent event =
        new CaseHearingEvent(UUID.randomUUID(), "src-1", null, null, longDescription, null);

    String timeline = CaseContextFormatter.formatTimeline(List.of(event));

    assertThat(timeline).startsWith("— — событие: ");
    assertThat(timeline).endsWith("...");
    assertThat(timeline.length()).isLessThan(longDescription.length());
  }

  @Test
  void formatChecklistReturnsPlaceholderWhenNoTasks() {
    assertThat(CaseContextFormatter.formatChecklist(List.of()))
        .isEqualTo("Задач по делу пока нет.");
  }

  @Test
  void formatChecklistFormatsDoneAndPendingTasksWithDueDate() {
    CaseTask done = new CaseTask();
    done.setText("Подготовить документы");
    done.setDone(true);

    CaseTask pending = new CaseTask();
    pending.setText("Подать иск");
    pending.setDone(false);
    pending.setDueDate(LocalDate.of(2026, 4, 10));

    String checklist = CaseContextFormatter.formatChecklist(List.of(done, pending));

    assertThat(checklist)
        .isEqualTo("[выполнено] Подготовить документы\n[в работе] Подать иск (срок 10.04.2026)");
  }
}
