package com.pravoos.user.collaboration.internal.dto;

public record CaseMessageNotificationResult(
        Long telegramChatId
) {
    public static CaseMessageNotificationResult none() {
        return new CaseMessageNotificationResult(null);
    }
}
