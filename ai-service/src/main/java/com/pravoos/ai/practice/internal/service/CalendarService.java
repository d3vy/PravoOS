package com.pravoos.ai.practice.internal.service;

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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {

  private static final UUID NIL_ORG_SENTINEL = new UUID(0L, 0L);
  private static final long MAX_RANGE_DAYS = 366;

  private final CaseRepository caseRepository;
  private final CaseHearingEventRepository hearingEventRepository;
  private final CaseTaskRepository caseTaskRepository;

  public CalendarService(
      CaseRepository caseRepository,
      CaseHearingEventRepository hearingEventRepository,
      CaseTaskRepository caseTaskRepository) {
    this.caseRepository = caseRepository;
    this.hearingEventRepository = hearingEventRepository;
    this.caseTaskRepository = caseTaskRepository;
  }

  @Transactional(readOnly = true)
  public List<CalendarEventResponse> findEvents(
      UUID lawyerId, List<UUID> orgIds, LocalDate from, LocalDate to, UUID caseId, UUID clientId) {
    validateRange(from, to);

    List<Case> cases =
        caseRepository.findVisibleForCalendar(lawyerId, orgIdsOrSentinel(orgIds), caseId, clientId);
    if (cases.isEmpty()) {
      return List.of();
    }
    Map<UUID, Case> casesById =
        cases.stream().collect(Collectors.toMap(Case::getId, Function.identity()));

    List<CalendarEventResponse> events = new ArrayList<>();
    collectDeadlines(events, cases, from, to);
    collectHearings(events, casesById, from, to);
    collectTasks(events, casesById, from, to);

    events.sort(
        Comparator.comparing(CalendarEventResponse::date)
            .thenComparing(event -> event.type().ordinal()));
    return events;
  }

  private void collectDeadlines(
      List<CalendarEventResponse> events, List<Case> cases, LocalDate from, LocalDate to) {
    for (Case caseEntity : cases) {
      addDeadline(
          events,
          caseEntity,
          DeadlineType.FILING_DEADLINE,
          caseEntity.getFilingDeadline(),
          from,
          to);
      addDeadline(
          events, caseEntity, DeadlineType.NEXT_HEARING, caseEntity.getNextHearingDate(), from, to);
      addDeadline(events, caseEntity, DeadlineType.EXPIRY, caseEntity.getExpiresAt(), from, to);
    }
  }

  private void addDeadline(
      List<CalendarEventResponse> events,
      Case caseEntity,
      DeadlineType type,
      LocalDate date,
      LocalDate from,
      LocalDate to) {
    if (date == null || date.isBefore(from) || date.isAfter(to)) {
      return;
    }
    events.add(
        new CalendarEventResponse(
            "deadline:" + caseEntity.getId() + ":" + type.name(),
            CalendarEventType.DEADLINE,
            CalendarEventType.DEADLINE.getDisplayName(),
            caseEntity.getId(),
            caseEntity.getTitle(),
            type.getDisplayName(),
            null,
            date));
  }

  private void collectHearings(
      List<CalendarEventResponse> events, Map<UUID, Case> casesById, LocalDate from, LocalDate to) {
    for (CaseHearingEvent hearing :
        hearingEventRepository.findByCaseIdInAndEventDateBetween(casesById.keySet(), from, to)) {
      Case caseEntity = casesById.get(hearing.getCaseId());
      if (caseEntity == null || hearing.getEventDate() == null) {
        continue;
      }
      String title =
          hearing.getEventType() != null && !hearing.getEventType().isBlank()
              ? hearing.getEventType()
              : CalendarEventType.HEARING.getDisplayName();
      events.add(
          new CalendarEventResponse(
              "hearing:" + hearing.getId(),
              CalendarEventType.HEARING,
              CalendarEventType.HEARING.getDisplayName(),
              caseEntity.getId(),
              caseEntity.getTitle(),
              title,
              hearing.getCourtName(),
              hearing.getEventDate()));
    }
  }

  private void collectTasks(
      List<CalendarEventResponse> events, Map<UUID, Case> casesById, LocalDate from, LocalDate to) {
    for (CaseTask task :
        caseTaskRepository.findByCaseIdInAndDoneFalseAndDueDateBetween(
            casesById.keySet(), from, to)) {
      Case caseEntity = casesById.get(task.getCaseId());
      if (caseEntity == null) {
        continue;
      }
      events.add(
          new CalendarEventResponse(
              "task:" + task.getId(),
              CalendarEventType.TASK,
              CalendarEventType.TASK.getDisplayName(),
              caseEntity.getId(),
              caseEntity.getTitle(),
              task.getText(),
              null,
              task.getDueDate()));
    }
  }

  private void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null) {
      throw new PravoosException("Не указан диапазон дат календаря", HttpStatus.BAD_REQUEST);
    }
    if (to.isBefore(from)) {
      throw new PravoosException("Дата окончания раньше даты начала", HttpStatus.BAD_REQUEST);
    }
    if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
      throw new PravoosException("Слишком большой диапазон дат календаря", HttpStatus.BAD_REQUEST);
    }
  }

  private Collection<UUID> orgIdsOrSentinel(List<UUID> orgIds) {
    return (orgIds == null || orgIds.isEmpty()) ? List.of(NIL_ORG_SENTINEL) : orgIds;
  }
}
