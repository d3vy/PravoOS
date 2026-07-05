package com.pravoos.user.collaboration.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DeadlineEmailRequest(
        @NotNull UUID lawyerId,
        @NotNull UUID caseId,
        @NotBlank String caseTitle,
        @NotBlank String deadlineTypeName,
        @NotBlank String deadlineDate,
        int daysLeft
) {}
