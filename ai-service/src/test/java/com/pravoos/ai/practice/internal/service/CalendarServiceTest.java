package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.dto.CalendarEventResponse;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseHearingEvent;
import com.pravoos.ai.practice.internal.model.entity.CaseTask;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.shared.exception.PravoosException;
import com.pravoos.ai.shared.model.enums.CalendarEventType;
import com.pravoos.ai.shared.model.enums.DeadlineType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

  @Mock private CaseRepository caseRepository;
  @Mock private CaseHearingEventRepository hearingEventRepository;
  @Mock private CaseTaskRepository caseTaskRepository;

  @InjectMocks private CalendarService calendarService;

  private final UUID lawyerId = UUID.randomUUID();
  private final List<UUID> orgIds = List.of(UUID.randomUUID());
  private final LocalDate from = LocalDate.of(2026, 1, 1);
  private final LocalDate to = LocalDate.of(2026, 1, 31);

  private Case caseWithId(UUID id, String title) {
    Case caseEntity = new Case();
    ReflectionTestUtils.setField(caseEntity, "id", id);
    caseEntity.setTitle(title);
    return caseEntity;
  }

  @Test
  void findEventsThrowsWhenFromOrToIsNull() {
    assertThatThrownBy(() -> calendarService.findEvents(lawyerId, orgIds, null, to, null, null))
        .isInstanceOf(PravoosException.class)
        .hasMessageContaining("Не указан диапазон");

    assertThatThrownBy(() -> calendarService.findEvents(lawyerId, orgIds, from, null, null, null))
        .isInstanceOf(PravoosException.class)
        .hasMessageContaining("Не указан диапазон");
  }

  @Test
  void findEventsThrowsWhenToIsBeforeFrom() {
    assertThatThrownBy(
            () ->
                calendarService.findEvents(
                    lawyerId,
                    orgIds,
                    LocalDate.of(2026, 2, 1),
                    LocalDate.of(2026, 1, 1),
                    null,
                    null))
        .isInstanceOf(PravoosException.class)
        .hasMessageContaining("раньше даты начала");
  }

  @Test
  void findEventsThrowsWhenRangeExceedsMaxDays() {
    LocalDate hugeTo = from.plusDays(367);
    assertThatThrownBy(() -> calendarService.findEvents(lawyerId, orgIds, from, hugeTo, null, null))
        .isInstanceOf(PravoosException.class)
        .hasMessageContaining("Слишком большой диапазон");
  }

  @Test
  void findEventsReturnsEmptyListWhenNoVisibleCases() {
    when(caseRepository.findVisibleForCalendar(any(), anyCollection(), any(), any()))
        .thenReturn(List.of());

    List<CalendarEventResponse> result =
        calendarService.findEvents(lawyerId, orgIds, from, to, null, null);

    assertThat(result).isEmpty();
  }

  @Test
  void findEventsUsesNilSentinelWhenOrgIdsIsNullOrEmpty() {
    when(caseRepository.findVisibleForCalendar(any(), anyCollection(), any(), any()))
        .thenReturn(List.of());

    calendarService.findEvents(lawyerId, null, from, to, null, null);
    calendarService.findEvents(lawyerId, List.of(), from, to, null, null);

    var captor = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
    org.mockito.Mockito.verify(caseRepository, org.mockito.Mockito.times(2))
        .findVisibleForCalendar(org.mockito.Mockito.eq(lawyerId), captor.capture(), any(), any());
    for (var captured : captor.getAllValues()) {
      assertThat(captured).containsExactly(new UUID(0L, 0L));
    }
  }

  @Test
  void findEventsCollectsDeadlinesWithinRangeOnly() {
    UUID caseId = UUID.randomUUID();
    Case caseEntity = caseWithId(caseId, "Дело №1");
    caseEntity.setFilingDeadline(LocalDate.of(2026, 1, 10));
    caseEntity.setNextHearingDate(LocalDate.of(2026, 2, 15)); // outside range
    caseEntity.setExpiresAt(null); // null, ignored

    when(caseRepository.findVisibleForCalendar(any(), anyCollection(), any(), any()))
        .thenReturn(List.of(caseEntity));
    when(hearingEventRepository.findByCaseIdInAndEventDateBetween(anyCollection(), any(), any()))
        .thenReturn(List.of());
    when(caseTaskRepository.findByCaseIdInAndDoneFalseAndDueDateBetween(
            anyCollection(), any(), any()))
        .thenReturn(List.of());

    List<CalendarEventResponse> result =
        calendarService.findEvents(lawyerId, orgIds, from, to, null, null);

    assertThat(result).hasSize(1);
    CalendarEventResponse event = result.get(0);
    assertThat(event.id())
        .isEqualTo("deadline:" + caseId + ":" + DeadlineType.FILING_DEADLINE.name());
    assertThat(event.type()).isEqualTo(CalendarEventType.DEADLINE);
    assertThat(event.typeName()).isEqualTo(CalendarEventType.DEADLINE.getDisplayName());
    assertThat(event.caseId()).isEqualTo(caseId);
    assertThat(event.caseTitle()).isEqualTo("Дело №1");
    assertThat(event.title()).isEqualTo(DeadlineType.FILING_DEADLINE.getDisplayName());
    assertThat(event.detail()).isNull();
    assertThat(event.date()).isEqualTo(LocalDate.of(2026, 1, 10));
  }

  @Test
  void findEventsCollectsHearingsAndSkipsUnknownCaseOrNullDate() {
    UUID caseId = UUID.randomUUID();
    Case caseEntity = caseWithId(caseId, "Дело №2");

    CaseHearingEvent typedHearing =
        new CaseHearingEvent(
            caseId, "src-1", LocalDate.of(2026, 1, 12), "Предварительное", null, "Мосгорсуд");
    ReflectionTestUtils.setField(typedHearing, "id", UUID.randomUUID());

    CaseHearingEvent blankTypeHearing =
        new CaseHearingEvent(caseId, "src-2", LocalDate.of(2026, 1, 13), "  ", null, null);
    ReflectionTestUtils.setField(blankTypeHearing, "id", UUID.randomUUID());

    CaseHearingEvent unknownCaseHearing =
        new CaseHearingEvent(
            UUID.randomUUID(), "src-3", LocalDate.of(2026, 1, 14), "X", null, null);
    ReflectionTestUtils.setField(unknownCaseHearing, "id", UUID.randomUUID());

    when(caseRepository.findVisibleForCalendar(any(), anyCollection(), any(), any()))
        .thenReturn(List.of(caseEntity));
    when(hearingEventRepository.findByCaseIdInAndEventDateBetween(anyCollection(), any(), any()))
        .thenReturn(List.of(typedHearing, blankTypeHearing, unknownCaseHearing));
    when(caseTaskRepository.findByCaseIdInAndDoneFalseAndDueDateBetween(
            anyCollection(), any(), any()))
        .thenReturn(List.of());

    List<CalendarEventResponse> result =
        calendarService.findEvents(lawyerId, orgIds, from, to, null, null);

    assertThat(result).hasSize(2);
    assertThat(result)
        .anySatisfy(
            e -> {
              assertThat(e.id()).isEqualTo("hearing:" + typedHearing.getId());
              assertThat(e.type()).isEqualTo(CalendarEventType.HEARING);
              assertThat(e.title()).isEqualTo("Предварительное");
              assertThat(e.detail()).isEqualTo("Мосгорсуд");
              assertThat(e.date()).isEqualTo(LocalDate.of(2026, 1, 12));
            })
        .anySatisfy(
            e -> {
              assertThat(e.id()).isEqualTo("hearing:" + blankTypeHearing.getId());
              assertThat(e.title()).isEqualTo(CalendarEventType.HEARING.getDisplayName());
            });
  }

  @Test
  void findEventsCollectsTasksAndSkipsUnknownCase() {
    UUID caseId = UUID.randomUUID();
    Case caseEntity = caseWithId(caseId, "Дело №3");

    CaseTask task = new CaseTask();
    ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
    task.setCaseId(caseId);
    task.setText("Подать документы");
    task.setDueDate(LocalDate.of(2026, 1, 20));
    task.setDone(false);

    CaseTask unknownCaseTask = new CaseTask();
    ReflectionTestUtils.setField(unknownCaseTask, "id", UUID.randomUUID());
    unknownCaseTask.setCaseId(UUID.randomUUID());
    unknownCaseTask.setDueDate(LocalDate.of(2026, 1, 21));

    when(caseRepository.findVisibleForCalendar(any(), anyCollection(), any(), any()))
        .thenReturn(List.of(caseEntity));
    when(hearingEventRepository.findByCaseIdInAndEventDateBetween(anyCollection(), any(), any()))
        .thenReturn(List.of());
    when(caseTaskRepository.findByCaseIdInAndDoneFalseAndDueDateBetween(
            anyCollection(), any(), any()))
        .thenReturn(List.of(task, unknownCaseTask));

    List<CalendarEventResponse> result =
        calendarService.findEvents(lawyerId, orgIds, from, to, null, null);

    assertThat(result).hasSize(1);
    CalendarEventResponse event = result.get(0);
    assertThat(event.id()).isEqualTo("task:" + task.getId());
    assertThat(event.type()).isEqualTo(CalendarEventType.TASK);
    assertThat(event.title()).isEqualTo("Подать документы");
    assertThat(event.detail()).isNull();
    assertThat(event.date()).isEqualTo(LocalDate.of(2026, 1, 20));
  }

  @Test
  void findEventsSortsByDateThenByTypeOrdinal() {
    UUID caseId = UUID.randomUUID();
    Case caseEntity = caseWithId(caseId, "Дело №4");
    caseEntity.setFilingDeadline(LocalDate.of(2026, 1, 15)); // DEADLINE, same date as hearing below

    CaseHearingEvent hearing =
        new CaseHearingEvent(caseId, "src-1", LocalDate.of(2026, 1, 15), "Заседание", null, null);
    ReflectionTestUtils.setField(hearing, "id", UUID.randomUUID());

    CaseTask task = new CaseTask();
    ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
    task.setCaseId(caseId);
    task.setText("Ранняя задача");
    task.setDueDate(LocalDate.of(2026, 1, 5));

    when(caseRepository.findVisibleForCalendar(any(), anyCollection(), any(), any()))
        .thenReturn(List.of(caseEntity));
    when(hearingEventRepository.findByCaseIdInAndEventDateBetween(anyCollection(), any(), any()))
        .thenReturn(List.of(hearing));
    when(caseTaskRepository.findByCaseIdInAndDoneFalseAndDueDateBetween(
            anyCollection(), any(), any()))
        .thenReturn(List.of(task));

    List<CalendarEventResponse> result =
        calendarService.findEvents(lawyerId, orgIds, from, to, null, null);

    assertThat(result).hasSize(3);
    // task (Jan 5) first, then on Jan 15: DEADLINE (ordinal 0) before HEARING (ordinal 1)
    assertThat(result.get(0).type()).isEqualTo(CalendarEventType.TASK);
    assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 5));
    assertThat(result.get(1).type()).isEqualTo(CalendarEventType.DEADLINE);
    assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2026, 1, 15));
    assertThat(result.get(2).type()).isEqualTo(CalendarEventType.HEARING);
    assertThat(result.get(2).date()).isEqualTo(LocalDate.of(2026, 1, 15));
  }
}
