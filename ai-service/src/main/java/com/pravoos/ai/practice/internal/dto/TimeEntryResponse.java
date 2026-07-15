package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.TimeEntry;
import com.pravoos.ai.practice.internal.util.BillingAmounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TimeEntryResponse(
        UUID id,
        UUID caseId,
        String description,
        LocalDate activityDate,
        int minutes,
        BigDecimal hourlyRate,
        BigDecimal amount,
        boolean billable,
        boolean running,
        Instant startedAt,
        boolean invoiced,
        LocalDateTime createdAt
) {
    public static TimeEntryResponse from(TimeEntry entry) {
        return new TimeEntryResponse(
                entry.getId(),
                entry.getCaseId(),
                entry.getDescription(),
                entry.getActivityDate(),
                entry.getMinutes(),
                entry.getHourlyRate(),
                BillingAmounts.lineAmount(entry.getMinutes(), entry.getHourlyRate()),
                entry.isBillable(),
                entry.isRunning(),
                entry.getStartedAt(),
                entry.isInvoiced(),
                entry.getCreatedAt()
        );
    }
}
