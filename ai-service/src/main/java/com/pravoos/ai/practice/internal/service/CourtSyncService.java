package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseHearingEvent;
import com.pravoos.ai.practice.internal.model.entity.CaseParty;
import com.pravoos.ai.practice.internal.model.entity.CaseTask;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CasePartyRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.shared.court.CourtCaseData;
import com.pravoos.ai.shared.court.CourtCaseProvider;
import com.pravoos.ai.shared.court.CourtCaseProviderRegistry;
import com.pravoos.ai.shared.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.ai.shared.service.OutboxEventService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourtSyncService {

  private static final Logger log = LoggerFactory.getLogger(CourtSyncService.class);
  private static final String TOPIC = "case.hearing.updated";
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final int PREPARATION_DAYS_BEFORE_HEARING = 3;

  private final CourtCaseProviderRegistry courtCaseProviderRegistry;
  private final CaseRepository caseRepository;
  private final CaseHearingEventRepository hearingEventRepository;
  private final CasePartyRepository casePartyRepository;
  private final CaseTaskRepository caseTaskRepository;
  private final OutboxEventService outboxEventService;
  private final CourtSyncService self;

  public CourtSyncService(
      CourtCaseProviderRegistry courtCaseProviderRegistry,
      CaseRepository caseRepository,
      CaseHearingEventRepository hearingEventRepository,
      CasePartyRepository casePartyRepository,
      CaseTaskRepository caseTaskRepository,
      OutboxEventService outboxEventService,
      @Lazy CourtSyncService self) {
    this.courtCaseProviderRegistry = courtCaseProviderRegistry;
    this.caseRepository = caseRepository;
    this.hearingEventRepository = hearingEventRepository;
    this.casePartyRepository = casePartyRepository;
    this.caseTaskRepository = caseTaskRepository;
    this.outboxEventService = outboxEventService;
    this.self = self;
  }

  public void syncCase(UUID caseId) {
    Case caseEntity = caseRepository.findById(caseId).orElse(null);
    if (caseEntity == null
        || caseEntity.getCourtCaseNumber() == null
        || caseEntity.getCourtCaseNumber().isBlank()) {
      return;
    }

    Optional<CourtCaseProvider> provider =
        courtCaseProviderRegistry.enabledFor(caseEntity.getCourtSystem());
    if (provider.isEmpty()) {
      log.debug(
          "Синхронизация дела {} пропущена: провайдер {} не подключён",
          caseId,
          caseEntity.getCourtSystem());
      return;
    }

    Optional<CourtCaseData> fetched = provider.get().fetchCase(caseEntity.getCourtCaseNumber());
    if (fetched.isEmpty()) {
      return;
    }

    self.persistSyncedCase(caseId, fetched.get());
  }

  @Transactional
  public void persistSyncedCase(UUID caseId, CourtCaseData data) {
    Case caseEntity = caseRepository.findById(caseId).orElse(null);
    if (caseEntity == null) {
      return;
    }

    int newEvents = persistNewEvents(caseId, data);
    if (data.caseGuid() != null && !data.caseGuid().equals(caseEntity.getCourtCaseGuid())) {
      caseEntity.setCourtCaseGuid(data.caseGuid());
    }
    applyHearingDate(caseEntity, data.nextHearingDate());
    applyJudge(caseEntity, data.judgeName());
    replaceParties(caseId, data);

    log.info(
        "{} sync: дело {} ({}) — новых событий {}, ближайшее заседание {}, судья {}",
        caseEntity.getCourtSystem().getDisplayName(),
        caseId,
        caseEntity.getCourtCaseNumber(),
        newEvents,
        data.nextHearingDate(),
        data.judgeName());
  }

  private void applyJudge(Case caseEntity, String judgeName) {
    if (judgeName != null && !judgeName.isBlank()) {
      caseEntity.setJudgeName(judgeName.trim());
    }
  }

  private void replaceParties(UUID caseId, CourtCaseData data) {
    if (data.parties() == null || data.parties().isEmpty()) {
      return;
    }
    Set<String> incoming =
        data.parties().stream()
            .map(party -> partyKey(party.name(), party.role()))
            .collect(Collectors.toSet());
    Set<String> current =
        casePartyRepository.findByCaseId(caseId).stream()
            .map(party -> partyKey(party.getName(), party.getRole()))
            .collect(Collectors.toSet());
    if (incoming.equals(current)) {
      return;
    }
    casePartyRepository.deleteByCaseId(caseId);
    for (CourtCaseData.CourtParty party : data.parties()) {
      casePartyRepository.save(new CaseParty(caseId, party.name(), party.role()));
    }
  }

  private String partyKey(String name, String role) {
    return (name == null ? "" : name.trim()) + ' ' + (role == null ? "" : role.trim());
  }

  private int persistNewEvents(UUID caseId, CourtCaseData data) {
    int saved = 0;
    for (CourtCaseData.CourtEvent event : data.events()) {
      if (event.sourceEventId() == null
          || hearingEventRepository.existsByCaseIdAndSourceEventId(caseId, event.sourceEventId())) {
        continue;
      }
      hearingEventRepository.save(
          new CaseHearingEvent(
              caseId,
              event.sourceEventId(),
              event.date(),
              event.type(),
              event.description(),
              event.courtName()));
      saved++;
    }
    return saved;
  }

  private void applyHearingDate(Case caseEntity, LocalDate newHearingDate) {
    if (newHearingDate == null) {
      return;
    }
    LocalDate previous = caseEntity.getNextHearingDate();
    if (newHearingDate.equals(previous)) {
      return;
    }
    caseEntity.setNextHearingDate(newHearingDate);
    publishHearingUpdated(caseEntity, previous, newHearingDate);
    createPreparationTask(caseEntity, newHearingDate);
  }

  private void createPreparationTask(Case caseEntity, LocalDate hearingDate) {
    if (hearingDate.isBefore(LocalDate.now())) {
      return;
    }
    String text = "Подготовиться к заседанию " + DATE_FORMATTER.format(hearingDate);
    boolean alreadyExists =
        caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseEntity.getId()).stream()
            .anyMatch(task -> !task.isDone() && text.equals(task.getText().trim()));
    if (alreadyExists) {
      return;
    }

    CaseTask task = new CaseTask();
    task.setCaseId(caseEntity.getId());
    task.setText(text);
    task.setDueDate(hearingDate.minusDays(PREPARATION_DAYS_BEFORE_HEARING));
    caseTaskRepository.save(task);
    log.info(
        "Created preparation task for case {} ahead of hearing {}",
        caseEntity.getId(),
        hearingDate);
  }

  private void publishHearingUpdated(
      Case caseEntity, LocalDate previous, LocalDate newHearingDate) {
    CaseHearingUpdatedKafkaPayload payload =
        new CaseHearingUpdatedKafkaPayload(
            caseEntity.getId(),
            caseEntity.getLawyerId(),
            caseEntity.getTitle(),
            caseEntity.getCourtCaseNumber(),
            previous == null ? null : DATE_FORMATTER.format(previous),
            DATE_FORMATTER.format(newHearingDate));
    outboxEventService.enqueue(TOPIC, caseEntity.getId().toString(), payload);
    log.info(
        "Enqueued case.hearing.updated: дело {} {} -> {}",
        caseEntity.getId(),
        previous,
        newHearingDate);
  }
}
