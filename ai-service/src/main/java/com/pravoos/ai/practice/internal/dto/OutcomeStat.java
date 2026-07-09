package com.pravoos.ai.practice.internal.dto;

public record OutcomeStat(
        String name,
        long totalCases,
        long wonCases,
        long lostCases,
        Integer winRatePercent
) {}
