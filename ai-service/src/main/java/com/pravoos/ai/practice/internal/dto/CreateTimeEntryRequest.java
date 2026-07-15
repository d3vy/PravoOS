package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTimeEntryRequest(
        @NotBlank @Size(max = 2000) String description,
        @NotNull LocalDate activityDate,
        @Min(1) @Max(1440) int minutes,
        @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal hourlyRate,
        boolean billable
) {}
