package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.DashboardResponse;
import com.pravoos.ai.practice.internal.dto.DashboardResponse.RecentCase;
import com.pravoos.ai.practice.internal.dto.DashboardResponse.StatusCount;
import com.pravoos.ai.practice.internal.dto.DashboardResponse.UpcomingDeadline;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.DeadlineType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final int DEADLINE_HORIZON_DAYS = 7;
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
        return new DashboardResponse(
                buildPipeline(lawyerId),
                caseRepository.countByLawyerIdAndStatusNotIn(lawyerId, CLOSED_STATUSES),
                caseTaskRepository.countOpenByLawyerId(lawyerId),
                buildUpcomingDeadlines(lawyerId),
                buildRecentCases(lawyerId)
        );
    }

    private List<StatusCount> buildPipeline(UUID lawyerId) {
        Map<CaseStatus, Long> counts = caseRepository.countGroupedByStatus(lawyerId).stream()
                .collect(Collectors.toMap(CaseRepository.StatusCountView::getStatus,
                        CaseRepository.StatusCountView::getCount));
        List<StatusCount> pipeline = new ArrayList<>();
        for (CaseStatus status : CaseStatus.values()) {
            pipeline.add(new StatusCount(status, status.getDisplayName(), counts.getOrDefault(status, 0L)));
        }
        return pipeline;
    }

    private List<UpcomingDeadline> buildUpcomingDeadlines(UUID lawyerId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate horizon = today.plusDays(DEADLINE_HORIZON_DAYS);
        List<UpcomingDeadline> deadlines = new ArrayList<>();
        for (Case caseEntity : caseRepository.findCasesWithUpcomingDeadlines(lawyerId, CLOSED_STATUSES, today, horizon)) {
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

    private List<RecentCase> buildRecentCases(UUID lawyerId) {
        return caseRepository.findTop5ByLawyerIdOrderByCreatedAtDesc(lawyerId).stream()
                .map(caseEntity -> new RecentCase(
                        caseEntity.getId(),
                        caseEntity.getTitle(),
                        caseEntity.getStatus(),
                        caseEntity.getStatus().getDisplayName(),
                        caseEntity.getCreatedAt()))
                .toList();
    }
}
