package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.entity.CaseHearingEvent;

import java.time.LocalDate;
import java.util.UUID;

public record CaseHearingEventResponse(
        UUID id,
        LocalDate eventDate,
        String eventType,
        String description,
        String courtName
) {
    public static CaseHearingEventResponse from(CaseHearingEvent event) {
        return new CaseHearingEventResponse(
                event.getId(),
                event.getEventDate(),
                event.getEventType(),
                event.getDescription(),
                event.getCourtName());
    }
}
