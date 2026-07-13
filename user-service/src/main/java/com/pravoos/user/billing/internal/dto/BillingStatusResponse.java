package com.pravoos.user.billing.internal.dto;

import com.pravoos.user.billing.internal.model.enums.SubscriptionStatus;

import java.time.LocalDateTime;

public record BillingStatusResponse(
        String planCode,
        String planName,
        SubscriptionStatus status,
        LocalDateTime trialEnd,
        LocalDateTime currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        int dailyRequests,
        long dailyTokens,
        int seats) {
}
