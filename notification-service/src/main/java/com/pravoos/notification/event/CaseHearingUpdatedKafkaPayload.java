package com.pravoos.notification.event;

import java.util.UUID;

public record CaseHearingUpdatedKafkaPayload(
        UUID caseId,
        UUID lawyerId,
        String caseTitle,
        String arbitrCaseNumber,
        String previousHearingDate,
        String newHearingDate
) {}
