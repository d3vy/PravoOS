package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.LegalAiAnswer;
import com.pravoos.ai.core.api.LegalAiPort;
import com.pravoos.ai.practice.internal.dto.AiCaseAnalysisDto;
import com.pravoos.ai.practice.internal.dto.CaseAnalyticsResponse;
import com.pravoos.ai.practice.internal.dto.CasePartyDto;
import com.pravoos.ai.practice.internal.dto.CaseTimelineStats;
import com.pravoos.ai.practice.internal.dto.EventTypeCount;
import com.pravoos.ai.practice.internal.dto.OutcomeStat;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseAnalysis;
import com.pravoos.ai.practice.internal.model.entity.CaseHearingEvent;
import com.pravoos.ai.practice.internal.model.entity.CaseParty;
import com.pravoos.ai.practice.internal.repository.jpa.CaseAnalysisRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CasePartyRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

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
    private final CasePartyRepository casePartyRepository;
    private final CaseAnalysisRepository caseAnalysisRepository;
    private final LegalAiPort legalAiPort;
    private final CaseAnalyticsService self;

    public CaseAnalyticsService(CaseService caseService,
                                CaseRepository caseRepository,
                                CaseHearingEventRepository hearingEventRepository,
                                CasePartyRepository casePartyRepository,
                                CaseAnalysisRepository caseAnalysisRepository,
                                LegalAiPort legalAiPort,
                                @Lazy CaseAnalyticsService self) {
        this.caseService = caseService;
        this.caseRepository = caseRepository;
        this.hearingEventRepository = hearingEventRepository;
        this.casePartyRepository = casePartyRepository;
        this.caseAnalysisRepository = caseAnalysisRepository;
        this.legalAiPort = legalAiPort;
        this.self = self;
    }

    @Transactional(readOnly = true)
    public CaseAnalyticsResponse getAnalytics(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        Case caseEntity = caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        return assemble(caseId, caseEntity, lawyerId, orgIds, caseAnalysisRepository.findByCaseId(caseId)
                .map(AiCaseAnalysisDto::from)
                .orElse(null));
    }

    public CaseAnalyticsResponse generateAnalysis(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        AnalysisInputs inputs = self.prepareAnalysis(caseId, lawyerId, orgIds);

        LegalAiAnswer answer = legalAiPort.analyzeCase(
                caseId, inputs.caseContext(), inputs.timelineText(), inputs.statisticsText(), lawyerId);

        CaseAnalysis saved;
        try {
            saved = self.persistAnalysis(caseId, lawyerId, answer, inputs.timeline().hearingCount());
        } catch (DataIntegrityViolationException concurrentInsert) {
            saved = self.persistAnalysis(caseId, lawyerId, answer, inputs.timeline().hearingCount());
        }

        log.info("Case analytics generated for case {} by lawyer {} ({} tokens)",
                caseId, lawyerId, answer.totalTokens());
        return new CaseAnalyticsResponse(inputs.timeline(), inputs.courtStats(), inputs.judgeStats(),
                inputs.partyStats(), AiCaseAnalysisDto.from(saved));
    }

    @Transactional(readOnly = true)
    public AnalysisInputs prepareAnalysis(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        Case caseEntity = caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        legalAiPort.assertWithinQuota(lawyerId);

        List<CaseHearingEvent> events = hearingEventRepository
                .findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId);
        List<CaseParty> parties = casePartyRepository.findByCaseId(caseId);
        CaseTimelineStats timeline = buildTimeline(caseEntity, events, parties);
        List<OutcomeStat> courtStats = buildCourtStats(timeline.courts(), lawyerId, orgIds);
        List<OutcomeStat> judgeStats = buildJudgeStats(caseEntity.getArbitrJudge(), lawyerId, orgIds);
        List<OutcomeStat> partyStats = buildPartyStats(parties, lawyerId, orgIds);

        return new AnalysisInputs(timeline, courtStats, judgeStats, partyStats,
                buildCaseContext(caseEntity, parties),
                buildTimelineText(events),
                buildStatisticsText(timeline, courtStats, judgeStats, partyStats));
    }

    @Transactional
    public CaseAnalysis persistAnalysis(UUID caseId, UUID lawyerId, LegalAiAnswer answer, int hearingCount) {
        return caseAnalysisRepository.findByCaseId(caseId)
                .map(existing -> {
                    existing.update(lawyerId, answer.content(), hearingCount, answer.totalTokens());
                    return existing;
                })
                .orElseGet(() -> caseAnalysisRepository.save(new CaseAnalysis(
                        caseId, lawyerId, answer.content(), hearingCount, answer.totalTokens())));
    }

    public record AnalysisInputs(CaseTimelineStats timeline, List<OutcomeStat> courtStats,
                                 List<OutcomeStat> judgeStats, List<OutcomeStat> partyStats,
                                 String caseContext, String timelineText, String statisticsText) {}

    private CaseAnalyticsResponse assemble(UUID caseId, Case caseEntity, UUID lawyerId, List<UUID> orgIds,
                                           AiCaseAnalysisDto aiAnalysis) {
        List<CaseHearingEvent> events = hearingEventRepository
                .findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId);
        List<CaseParty> parties = casePartyRepository.findByCaseId(caseId);
        CaseTimelineStats timeline = buildTimeline(caseEntity, events, parties);
        return new CaseAnalyticsResponse(
                timeline,
                buildCourtStats(timeline.courts(), lawyerId, orgIds),
                buildJudgeStats(caseEntity.getArbitrJudge(), lawyerId, orgIds),
                buildPartyStats(parties, lawyerId, orgIds),
                aiAnalysis);
    }

    private CaseTimelineStats buildTimeline(Case caseEntity, List<CaseHearingEvent> events,
                                            List<CaseParty> parties) {
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

        LocalDate nextHearingDate = caseEntity.getNextHearingDate();
        Integer daysToNextHearing = nextHearingDate == null
                ? null
                : (int) ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), nextHearingDate);

        return new CaseTimelineStats(events.size(), firstEventDate, lastEventDate, spanDays,
                averageIntervalDays, courts, buildEventTypeCounts(events), nextHearingDate,
                daysToNextHearing, caseEntity.getArbitrJudge(),
                parties.stream().map(CasePartyDto::from).toList());
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

    private List<OutcomeStat> buildCourtStats(List<String> courts, UUID lawyerId, List<UUID> orgIds) {
        return toStats(courts, (names) -> caseRepository.courtStatistics(
                names, lawyerId, orgIdsOrSentinel(orgIds), CaseStatus.CLOSED_WON, CaseStatus.CLOSED_LOST));
    }

    private List<OutcomeStat> buildJudgeStats(String judge, UUID lawyerId, List<UUID> orgIds) {
        if (judge == null || judge.isBlank()) {
            return List.of();
        }
        return toStats(List.of(judge.trim()), (names) -> caseRepository.judgeStatistics(
                names, lawyerId, orgIdsOrSentinel(orgIds), CaseStatus.CLOSED_WON, CaseStatus.CLOSED_LOST));
    }

    private List<OutcomeStat> buildPartyStats(List<CaseParty> parties, UUID lawyerId, List<UUID> orgIds) {
        List<String> names = parties.stream()
                .map(CaseParty::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        return toStats(names, (values) -> caseRepository.partyStatistics(
                values, lawyerId, orgIdsOrSentinel(orgIds), CaseStatus.CLOSED_WON, CaseStatus.CLOSED_LOST));
    }

    private List<OutcomeStat> toStats(List<String> names,
                                      Function<Collection<String>, List<CaseRepository.OutcomeStatView>> query) {
        if (names.isEmpty()) {
            return List.of();
        }
        return query.apply(names).stream()
                .map(view -> {
                    long decided = view.getWonCases() + view.getLostCases();
                    Integer winRate = decided == 0
                            ? null
                            : (int) Math.round(view.getWonCases() * 100.0 / decided);
                    return new OutcomeStat(view.getName(), view.getTotalCases(),
                            view.getWonCases(), view.getLostCases(), winRate);
                })
                .sorted(Comparator.comparingLong(OutcomeStat::totalCases).reversed())
                .toList();
    }

    private String buildCaseContext(Case caseEntity, List<CaseParty> parties) {
        StringBuilder builder = new StringBuilder();
        builder.append("Название: ").append(caseEntity.getTitle()).append('\n');
        builder.append("Статус: ").append(caseEntity.getStatus().getDisplayName()).append('\n');
        if (caseEntity.getArbitrCaseNumber() != null) {
            builder.append("Номер в КАД.Арбитр: ").append(caseEntity.getArbitrCaseNumber()).append('\n');
        }
        if (caseEntity.getArbitrJudge() != null) {
            builder.append("Судья: ").append(caseEntity.getArbitrJudge()).append('\n');
        }
        if (!parties.isEmpty()) {
            builder.append("Стороны: ");
            builder.append(parties.stream()
                    .map(party -> party.getRole() == null
                            ? party.getName()
                            : party.getName() + " (" + party.getRole() + ")")
                    .reduce((first, second) -> first + "; " + second)
                    .orElse(""));
            builder.append('\n');
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

    private String buildStatisticsText(CaseTimelineStats timeline, List<OutcomeStat> courtStats,
                                       List<OutcomeStat> judgeStats, List<OutcomeStat> partyStats) {
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
        appendOutcomeStats(builder, "Суд", courtStats);
        appendOutcomeStats(builder, "Судья", judgeStats);
        appendOutcomeStats(builder, "Сторона", partyStats);
        return builder.toString().strip();
    }

    private void appendOutcomeStats(StringBuilder builder, String label, List<OutcomeStat> stats) {
        for (OutcomeStat stat : stats) {
            builder.append(label).append(" «").append(stat.name()).append("»: дел у юриста ")
                    .append(stat.totalCases());
            if (stat.winRatePercent() != null) {
                builder.append(", доля выигранных ").append(stat.winRatePercent()).append('%')
                        .append(" (выиграно ").append(stat.wonCases())
                        .append(", проиграно ").append(stat.lostCases()).append(')');
            }
            builder.append('\n');
        }
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
