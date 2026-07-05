package com.pravoos.user.model.dto;

public record CaseMessageNotificationResult(
        Long telegramChatId
) {
    public static CaseMessageNotificationResult none() {
        return new CaseMessageNotificationResult(null);
    }
}
