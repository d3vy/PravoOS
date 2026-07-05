package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.DeadlineType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        List<StatusCount> pipeline,
        long activeCases,
        long openTasks,
        List<UpcomingDeadline> upcomingDeadlines,
        List<RecentCase> recentCases
) {
    public record StatusCount(CaseStatus status, String statusName, long count) {}

    public record UpcomingDeadline(
            UUID caseId,
            String caseTitle,
            DeadlineType type,
            String typeName,
            LocalDate date,
            long daysLeft
    ) {}

    public record RecentCase(
            UUID id,
            String title,
            CaseStatus status,
            String statusName,
            LocalDateTime createdAt
    ) {}
}
