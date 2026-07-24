package com.pravoos.user.billing.internal.dto;

public record PlanResponse(
    String code,
    String name,
    long priceKopecks,
    int dailyRequests,
    long dailyTokens,
    int seats,
    boolean isDefault) {}
