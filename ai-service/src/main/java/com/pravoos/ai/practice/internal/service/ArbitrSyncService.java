package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.service.OutboxEventService;
import com.pravoos.ai.arbitr.ArbitrCaseData;
import com.pravoos.ai.arbitr.ArbitrCaseProvider;
import com.pravoos.ai.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseHearingEvent;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Service
public class ArbitrSyncService {

    private static final Logger log = LoggerFactory.getLogger(ArbitrSyncService.class);
    private static final String TOPIC = "case.hearing.updated";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ArbitrCaseProvider arbitrCaseProvider;
    private final CaseRepository caseRepository;
    private final CaseHearingEventRepository hearingEventRepository;
    private final OutboxEventService outboxEventService;
    private final ArbitrSyncService self;

    public ArbitrSyncService(ArbitrCaseProvider arbitrCaseProvider,
                             CaseRepository caseRepository,
                             CaseHearingEventRepository hearingEventRepository,
                             OutboxEventService outboxEventService,
                             @Lazy ArbitrSyncService self) {
        this.arbitrCaseProvider = arbitrCaseProvider;
        this.caseRepository = caseRepository;
        this.hearingEventRepository = hearingEventRepository;
        this.outboxEventService = outboxEventService;
        this.self = self;
    }

    public void syncCase(UUID caseId) {
        Case caseEntity = caseRepository.findById(caseId).orElse(null);
        if (caseEntity == null || caseEntity.getArbitrCaseNumber() == null
                || caseEntity.getArbitrCaseNumber().isBlank()) {
            return;
        }

        Optional<ArbitrCaseData> fetched = arbitrCaseProvider.fetchCase(caseEntity.getArbitrCaseNumber());
        if (fetched.isEmpty()) {
            return;
        }

        self.persistSyncedCase(caseId, fetched.get());
    }

    @Transactional
    public void persistSyncedCase(UUID caseId, ArbitrCaseData data) {
        Case caseEntity = caseRepository.findById(caseId).orElse(null);
        if (caseEntity == null) {
            return;
        }

        int newEvents = persistNewEvents(caseId, data);
        if (data.caseGuid() != null && !data.caseGuid().equals(caseEntity.getArbitrCaseGuid())) {
            caseEntity.setArbitrCaseGuid(data.caseGuid());
        }
        applyHearingDate(caseEntity, data.nextHearingDate());

        log.info("КАД.Арбитр sync: дело {} ({}) — новых событий {}, ближайшее заседание {}",
                caseId, caseEntity.getArbitrCaseNumber(), newEvents, data.nextHearingDate());
    }

    private int persistNewEvents(UUID caseId, ArbitrCaseData data) {
        int saved = 0;
        for (ArbitrCaseData.ArbitrEvent event : data.events()) {
            if (event.sourceEventId() == null
                    || hearingEventRepository.existsByCaseIdAndSourceEventId(caseId, event.sourceEventId())) {
                continue;
            }
            hearingEventRepository.save(new CaseHearingEvent(
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
    }

    private void publishHearingUpdated(Case caseEntity, LocalDate previous, LocalDate newHearingDate) {
        CaseHearingUpdatedKafkaPayload payload = new CaseHearingUpdatedKafkaPayload(
                caseEntity.getId(),
                caseEntity.getLawyerId(),
                caseEntity.getTitle(),
                caseEntity.getArbitrCaseNumber(),
                previous == null ? null : DATE_FORMATTER.format(previous),
                DATE_FORMATTER.format(newHearingDate));
        outboxEventService.enqueue(TOPIC, caseEntity.getId().toString(), payload);
        log.info("Enqueued case.hearing.updated: дело {} {} -> {}",
                caseEntity.getId(), previous, newHearingDate);
    }
}
