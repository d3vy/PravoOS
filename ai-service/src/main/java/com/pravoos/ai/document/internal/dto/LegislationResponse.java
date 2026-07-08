package com.pravoos.ai.document.internal.dto;

import com.pravoos.ai.shared.model.enums.DocumentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record LegislationResponse(
        UUID id,
        String title,
        String actCanonical,
        String articleNumber,
        LocalDate editionDate,
        DocumentStatus status,
        boolean superseded
) {}
