package com.pravoos.user.identity.internal.dto;

public record NotificationSettingsResponse(
        boolean loginAlertEmail,
        boolean loginAlertTelegram,
        boolean caseMessageEmail,
        boolean caseMessageTelegram,
        boolean telegramLinked
) {}
