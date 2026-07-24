package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCaseRequest(
    @NotBlank @Size(max = 500) String title,
    @Size(max = 5000) String description,
    UUID clientId,
    UUID orgId,
    LocalDate filingDeadline,
    LocalDate nextHearingDate,
    LocalDate expiresAt,
    @Size(max = 50) String arbitrCaseNumber,
    @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal defaultHourlyRate) {}
