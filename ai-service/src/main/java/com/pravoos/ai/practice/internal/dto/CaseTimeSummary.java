package com.pravoos.ai.practice.internal.dto;

import java.math.BigDecimal;
import java.util.List;

public record CaseTimeSummary(
        List<TimeEntryResponse> entries,
        int totalMinutes,
        int billableMinutes,
        int uninvoicedBillableMinutes,
        BigDecimal billableAmount,
        BigDecimal uninvoicedBillableAmount
) {}
