package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceRequest(
        @NotNull UUID clientId,
        UUID caseId,
        List<UUID> timeEntryIds,
        LocalDate dueDate,
        @Size(max = 4000) String notes
) {}
