package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateCaseRequest(
        @NotBlank @Size(max = 500) String title,
        @Size(max = 5000) String description,
        UUID clientId,
        LocalDate filingDeadline,
        LocalDate nextHearingDate,
        LocalDate expiresAt,
        @Size(max = 50) String arbitrCaseNumber
) {}
