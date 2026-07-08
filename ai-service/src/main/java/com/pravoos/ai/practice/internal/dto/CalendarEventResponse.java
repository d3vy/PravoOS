package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.CalendarEventType;

import java.time.LocalDate;
import java.util.UUID;

public record CalendarEventResponse(
        String id,
        CalendarEventType type,
        String typeName,
        UUID caseId,
        String caseTitle,
        String title,
        String detail,
        LocalDate date
) {}
