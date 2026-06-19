package com.pravoos.ai.service;

import com.pravoos.ai.model.dto.DashboardResponse;
import com.pravoos.ai.model.dto.DashboardResponse.RecentCase;
import com.pravoos.ai.model.dto.DashboardResponse.StatusCount;
import com.pravoos.ai.model.dto.DashboardResponse.UpcomingDeadline;
import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.model.enums.CaseStatus;
import com.pravoos.ai.model.enums.DeadlineType;
import com.pravoos.ai.repository.jpa.CaseRepository;
import com.pravoos.ai.repository.jpa.CaseTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final int DEADLINE_HORIZON_DAYS = 7;
    private static final int RECENT_CASES_LIMIT = 5;
    private static final Set<CaseStatus> CLOSED_STATUSES =
            EnumSet.of(CaseStatus.CLOSED_WON, CaseStatus.CLOSED_LOST);

    private final CaseRepository caseRepository;
    private final CaseTaskRepository caseTaskRepository;

    public DashboardService(CaseRepository caseRepository, CaseTaskRepository caseTaskRepository) {
        this.caseRepository = caseRepository;
        this.caseTaskRepository = caseTaskRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID lawyerId) {
        List<Case> cases = caseRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId);

        return new DashboardResponse(
                buildPipeline(cases),
                countActive(cases),
                caseTaskRepository.countOpenByLawyerId(lawyerId),
                buildUpcomingDeadlines(cases),
                buildRecentCases(cases)
        );
    }

    private List<StatusCount> buildPipeline(List<Case> cases) {
        Map<CaseStatus, Long> counts = cases.stream()
                .collect(Collectors.groupingBy(Case::getStatus, Collectors.counting()));
        List<StatusCount> pipeline = new ArrayList<>();
        for (CaseStatus status : CaseStatus.values()) {
            pipeline.add(new StatusCount(status, status.getDisplayName(), counts.getOrDefault(status, 0L)));
        }
        return pipeline;
    }

    private long countActive(List<Case> cases) {
        return cases.stream().filter(caseEntity -> !isClosed(caseEntity)).count();
    }

    private List<UpcomingDeadline> buildUpcomingDeadlines(List<Case> cases) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate horizon = today.plusDays(DEADLINE_HORIZON_DAYS);
        List<UpcomingDeadline> deadlines = new ArrayList<>();
        for (Case caseEntity : cases) {
            if (isClosed(caseEntity)) {
                continue;
            }
            collectDeadline(deadlines, caseEntity, DeadlineType.FILING_DEADLINE, Case::getFilingDeadline, today, horizon);
            collectDeadline(deadlines, caseEntity, DeadlineType.NEXT_HEARING, Case::getNextHearingDate, today, horizon);
            collectDeadline(deadlines, caseEntity, DeadlineType.EXPIRY, Case::getExpiresAt, today, horizon);
        }
        deadlines.sort(Comparator.comparing(UpcomingDeadline::date));
        return deadlines;
    }

    private void collectDeadline(List<UpcomingDeadline> deadlines,
                                 Case caseEntity,
                                 DeadlineType type,
                                 Function<Case, LocalDate> dateAccessor,
                                 LocalDate today,
                                 LocalDate horizon) {
        LocalDate date = dateAccessor.apply(caseEntity);
        if (date == null || date.isBefore(today) || date.isAfter(horizon)) {
            return;
        }
        deadlines.add(new UpcomingDeadline(
                caseEntity.getId(),
                caseEntity.getTitle(),
                type,
                type.getDisplayName(),
                date,
                ChronoUnit.DAYS.between(today, date)
        ));
    }

    private List<RecentCase> buildRecentCases(List<Case> cases) {
        return cases.stream()
                .limit(RECENT_CASES_LIMIT)
                .map(caseEntity -> new RecentCase(
                        caseEntity.getId(),
                        caseEntity.getTitle(),
                        caseEntity.getStatus(),
                        caseEntity.getStatus().getDisplayName(),
                        caseEntity.getCreatedAt()))
                .toList();
    }

    private boolean isClosed(Case caseEntity) {
        return CLOSED_STATUSES.contains(caseEntity.getStatus());
    }
}
