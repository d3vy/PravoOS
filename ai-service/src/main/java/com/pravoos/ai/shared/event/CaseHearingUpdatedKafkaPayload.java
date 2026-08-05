package com.pravoos.ai.shared.event;

import java.util.UUID;

public record CaseHearingUpdatedKafkaPayload(
    UUID caseId,
    UUID lawyerId,
    String caseTitle,
    String courtCaseNumber,
    String previousHearingDate,
    String newHearingDate) {}
