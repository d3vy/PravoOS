package com.pravoos.ai.arbitr;

import java.time.LocalDate;
import java.util.List;

public record ArbitrCaseData(
        String caseNumber,
        String caseGuid,
        LocalDate nextHearingDate,
        List<ArbitrEvent> events
) {
    public record ArbitrEvent(
            String sourceEventId,
            LocalDate date,
            String type,
            String description,
            String courtName
    ) {}
}
