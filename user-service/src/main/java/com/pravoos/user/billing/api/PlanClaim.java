package com.pravoos.user.billing.api;

public record PlanClaim(String code, int dailyRequests, long dailyTokens) {}
