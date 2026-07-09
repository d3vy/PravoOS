package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.LegalAiAnswer;
import com.pravoos.ai.core.api.LegalAiPort;
import com.pravoos.ai.practice.internal.dto.AiCaseAnalysisDto;
import com.pravoos.ai.practice.internal.dto.CaseAnalyticsResponse;
import com.pravoos.ai.practice.internal.dto.CaseTimelineStats;
import com.pravoos.ai.practice.internal.dto.CourtStat;
import com.pravoos.ai.practice.internal.dto.EventTypeCount;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseAnalysis;
import com.pravoos.ai.practice.internal.model.entity.CaseHearingEvent;
import com.pravoos.ai.practice.internal.repository.jpa.CaseAnalysisRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class CaseAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(CaseAnalyticsService.class);
    private static final UUID NIL_ORG_SENTINEL = new UUID(0L, 0L);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int MAX_TIMELINE_EVENTS = 60;
    private static final int MAX_EVENT_TYPES = 8;
    private static final int EVENT_DESCRIPTION_MAX_LENGTH = 300;

    private final CaseService caseService;
    private final CaseRepository caseRepository;
    private final CaseHearingEventRepository hearingEventRepository;
    private final CaseAnalysisRepository caseAnalysisRepository;
    private final LegalAiPort legalAiPort;

    public CaseAnalyticsService(CaseService caseService,
                                CaseRepository caseRepository,
                                CaseHearingEventRepository hearingEventRepository,
                                CaseAnalysisRepository caseAnalysisRepository,
                                LegalAiPort legalAiPort) {
        this.caseService = caseService;
        this.caseRepository = caseRepository;
        this.hearingEventRepository = hearingEventRepository;
        this.caseAnalysisRepository = caseAnalysisRepository;
        this.legalAiPort = legalAiPort;
    }

    @Transactional(readOnly = true)
    public CaseAnalyticsResponse getAnalytics(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        Case caseEntity = caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        List<CaseHearingEvent> events = hearingEventRepository
                .findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId);

        CaseTimelineStats timeline = buildTimeline(caseEntity, events);
        List<CourtStat> courtStats = buildCourtStats(timeline.courts(), lawyerId, orgIds);
        AiCaseAnalysisDto aiAnalysis = caseAnalysisRepository.findByCaseId(caseId)
                .map(AiCaseAnalysisDto::from)
                .orElse(null);

        return new CaseAnalyticsResponse(timeline, courtStats, aiAnalysis);
    }

    @Transactional
    public CaseAnalyticsResponse generateAnalysis(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        Case caseEntity = caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        legalAiPort.assertWithinQuota(lawyerId);

        List<CaseHearingEvent> events = hearingEventRepository
                .findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId);
        CaseTimelineStats timeline = buildTimeline(caseEntity, events);
        List<CourtStat> courtStats = buildCourtStats(timeline.courts(), lawyerId, orgIds);

        LegalAiAnswer answer = legalAiPort.analyzeCase(
                caseId,
                buildCaseContext(caseEntity),
                buildTimelineText(events),
                buildStatisticsText(timeline, courtStats),
                lawyerId);

        CaseAnalysis analysis = caseAnalysisRepository.findByCaseId(caseId)
                .map(existing -> {
                    existing.update(lawyerId, answer.content(), timeline.hearingCount(), answer.totalTokens());
                    return existing;
                })
                .orElseGet(() -> new CaseAnalysis(
                        caseId, lawyerId, answer.content(), timeline.hearingCount(), answer.totalTokens()));
        CaseAnalysis saved = caseAnalysisRepository.save(analysis);

        log.info("Case analytics generated for case {} by lawyer {} ({} tokens)",
                caseId, lawyerId, answer.totalTokens());
        return new CaseAnalyticsResponse(timeline, courtStats, AiCaseAnalysisDto.from(saved));
    }

    private CaseTimelineStats buildTimeline(Case caseEntity, List<CaseHearingEvent> events) {
        List<LocalDate> eventDates = events.stream()
                .map(CaseHearingEvent::getEventDate)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        LocalDate firstEventDate = eventDates.isEmpty() ? null : eventDates.get(0);
        LocalDate lastEventDate = eventDates.isEmpty() ? null : eventDates.get(eventDates.size() - 1);
        Integer spanDays = firstEventDate == null
                ? null
                : (int) ChronoUnit.DAYS.between(firstEventDate, lastEventDate);
        Integer averageIntervalDays = eventDates.size() < 2
                ? null
                : Math.round((float) spanDays / (eventDates.size() - 1));

        List<String> courts = events.stream()
                .map(CaseHearingEvent::getCourtName)
                .filter(court -> court != null && !court.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        List<EventTypeCount> eventTypes = buildEventTypeCounts(events);

        LocalDate nextHearingDate = caseEntity.getNextHearingDate();
        Integer daysToNextHearing = nextHearingDate == null
                ? null
                : (int) ChronoUnit.DAYS.between(LocalDate.now(), nextHearingDate);

        return new CaseTimelineStats(events.size(), firstEventDate, lastEventDate, spanDays,
                averageIntervalDays, courts, eventTypes, nextHearingDate, daysToNextHearing);
    }

    private List<EventTypeCount> buildEventTypeCounts(List<CaseHearingEvent> events) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (CaseHearingEvent event : events) {
            String type = (event.getEventType() == null || event.getEventType().isBlank())
                    ? "Прочее"
                    : event.getEventType().trim();
            counts.merge(type, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_EVENT_TYPES)
                .map(entry -> new EventTypeCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<CourtStat> buildCourtStats(List<String> courts, UUID lawyerId, List<UUID> orgIds) {
        if (courts.isEmpty()) {
            return List.of();
        }
        return caseRepository.courtStatistics(courts, lawyerId, orgIdsOrSentinel(orgIds),
                        CaseStatus.CLOSED_WON, CaseStatus.CLOSED_LOST)
                .stream()
                .map(view -> {
                    long decided = view.getWonCases() + view.getLostCases();
                    Integer winRate = decided == 0
                            ? null
                            : (int) Math.round(view.getWonCases() * 100.0 / decided);
                    return new CourtStat(view.getCourtName(), view.getTotalCases(),
                            view.getWonCases(), view.getLostCases(), winRate);
                })
                .sorted(Comparator.comparingLong(CourtStat::totalCases).reversed())
                .toList();
    }

    private String buildCaseContext(Case caseEntity) {
        StringBuilder builder = new StringBuilder();
        builder.append("Название: ").append(caseEntity.getTitle()).append('\n');
        builder.append("Статус: ").append(caseEntity.getStatus().getDisplayName()).append('\n');
        if (caseEntity.getArbitrCaseNumber() != null) {
            builder.append("Номер в КАД.Арбитр: ").append(caseEntity.getArbitrCaseNumber()).append('\n');
        }
        if (caseEntity.getNextHearingDate() != null) {
            builder.append("Ближайшее заседание: ")
                    .append(DATE_FORMATTER.format(caseEntity.getNextHearingDate())).append('\n');
        }
        if (caseEntity.getDescription() != null && !caseEntity.getDescription().isBlank()) {
            builder.append("Описание: ").append(caseEntity.getDescription());
        }
        return builder.toString().strip();
    }

    private String buildTimelineText(List<CaseHearingEvent> events) {
        if (events.isEmpty()) {
            return "Событий по делу из КАД.Арбитр пока нет.";
        }
        StringBuilder builder = new StringBuilder();
        events.stream().limit(MAX_TIMELINE_EVENTS).forEach(event -> {
            builder.append(event.getEventDate() == null ? "—" : DATE_FORMATTER.format(event.getEventDate()));
            builder.append(" — ").append(event.getEventType() == null ? "событие" : event.getEventType());
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

    private String buildStatisticsText(CaseTimelineStats timeline, List<CourtStat> courtStats) {
        StringBuilder builder = new StringBuilder();
        builder.append("Всего событий: ").append(timeline.hearingCount()).append('\n');
        if (timeline.firstEventDate() != null) {
            builder.append("Период рассмотрения: ")
                    .append(DATE_FORMATTER.format(timeline.firstEventDate()))
                    .append(" — ").append(DATE_FORMATTER.format(timeline.lastEventDate()))
                    .append(" (").append(timeline.spanDays()).append(" дн.)\n");
        }
        if (timeline.averageIntervalDays() != null) {
            builder.append("Средний интервал между событиями: ")
                    .append(timeline.averageIntervalDays()).append(" дн.\n");
        }
        if (timeline.daysToNextHearing() != null) {
            builder.append("До ближайшего заседания: ")
                    .append(timeline.daysToNextHearing()).append(" дн.\n");
        }
        for (CourtStat court : courtStats) {
            builder.append("Суд «").append(court.courtName()).append("»: дел у юриста ")
                    .append(court.totalCases());
            if (court.winRatePercent() != null) {
                builder.append(", доля выигранных ").append(court.winRatePercent()).append('%')
                        .append(" (выиграно ").append(court.wonCases())
                        .append(", проиграно ").append(court.lostCases()).append(')');
            }
            builder.append('\n');
        }
        return builder.toString().strip();
    }

    private String truncate(String text) {
        String trimmed = text.trim();
        return trimmed.length() <= EVENT_DESCRIPTION_MAX_LENGTH
                ? trimmed
                : trimmed.substring(0, EVENT_DESCRIPTION_MAX_LENGTH) + "...";
    }

    private Collection<UUID> orgIdsOrSentinel(List<UUID> orgIds) {
        return (orgIds == null || orgIds.isEmpty()) ? List.of(NIL_ORG_SENTINEL) : orgIds;
    }
}
