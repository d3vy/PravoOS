package com.pravoos.ai.practice.internal.dto;

public record CourtStat(
        String courtName,
        long totalCases,
        long wonCases,
        long lostCases,
        Integer winRatePercent
) {}
