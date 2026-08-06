package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceRequest(
    @NotNull UUID clientId,
    UUID caseId,
    List<UUID> timeEntryIds,
    LocalDate dueDate,
    @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 2) BigDecimal vatRate,
    @Size(max = 4000) String notes) {}
