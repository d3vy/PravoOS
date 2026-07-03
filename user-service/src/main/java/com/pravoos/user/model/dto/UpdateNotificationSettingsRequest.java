package com.pravoos.user.model.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingsRequest(
        @NotNull Boolean loginAlertEmail,
        @NotNull Boolean loginAlertTelegram
) {}
