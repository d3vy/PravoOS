package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StartTimerRequest(
        @NotBlank @Size(max = 2000) String description,
        @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal hourlyRate,
        boolean billable
) {}
