package com.pravoos.user.identity.internal.dto;

public record NotificationSettingsResponse(
    boolean loginAlertEmail,
    boolean loginAlertTelegram,
    boolean loginAlertPush,
    boolean caseMessageEmail,
    boolean caseMessageTelegram,
    boolean caseMessagePush,
    boolean telegramLinked) {}
