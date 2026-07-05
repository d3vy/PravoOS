package com.pravoos.user.collaboration.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CaseMessageNotificationRequest(
        @NotNull UUID caseId,
        @NotBlank String caseTitle,
        @NotBlank String authorRole,
        UUID recipientLawyerId,
        UUID recipientClientId,
        String preview
) {}
