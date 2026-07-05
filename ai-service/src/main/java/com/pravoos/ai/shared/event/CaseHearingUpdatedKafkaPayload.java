package com.pravoos.ai.shared.event;

import java.util.UUID;

public record CaseHearingUpdatedKafkaPayload(
        UUID caseId,
        UUID lawyerId,
        String caseTitle,
        String arbitrCaseNumber,
        String previousHearingDate,
        String newHearingDate
) {}
