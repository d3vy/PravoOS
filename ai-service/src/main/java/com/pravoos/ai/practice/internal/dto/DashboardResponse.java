package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.DeadlineType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        List<StatusCount> pipeline,
        long activeCases,
        long openTasks,
        List<UpcomingDeadline> upcomingDeadlines,
        List<RecentCase> recentCases,
        MoneyOnTable moneyOnTable,
        List<TodayTask> tasksToday,
        UnpaidInvoicesSummary unpaidInvoices
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

    public record MoneyOnTable(int uninvoicedMinutes, BigDecimal uninvoicedAmount) {}

    public record TodayTask(
            UUID id,
            UUID caseId,
            String caseTitle,
            String text,
            LocalDate dueDate,
            long daysOverdue
    ) {}

    public record UnpaidInvoiceItem(
            UUID id,
            String number,
            String clientName,
            BigDecimal total,
            String currency,
            LocalDate dueDate,
            long daysOverdue
    ) {}

    public record UnpaidInvoicesSummary(long count, BigDecimal totalAmount, List<UnpaidInvoiceItem> items) {}
}
