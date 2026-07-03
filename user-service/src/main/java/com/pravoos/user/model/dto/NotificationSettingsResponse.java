package com.pravoos.user.model.dto;

public record NotificationSettingsResponse(
        boolean loginAlertEmail,
        boolean loginAlertTelegram,
        boolean telegramLinked
) {}
