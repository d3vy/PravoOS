package com.pravoos.user.identity.internal.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingsRequest(
        @NotNull Boolean loginAlertEmail,
        @NotNull Boolean loginAlertTelegram,
        @NotNull Boolean caseMessageEmail,
        @NotNull Boolean caseMessageTelegram
) {}
