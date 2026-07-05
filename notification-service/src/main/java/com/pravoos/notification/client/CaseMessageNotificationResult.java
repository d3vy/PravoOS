package com.pravoos.notification.client;

public record CaseMessageNotificationResult(
        Long telegramChatId
) {
    public static CaseMessageNotificationResult none() {
        return new CaseMessageNotificationResult(null);
    }
}
