package com.pravoos.ai.shared.event;

import java.util.UUID;

public record CaseDeadlineKafkaPayload(
    UUID caseId,
    UUID lawyerId,
    String caseTitle,
    String deadlineTypeName,
    String deadlineDate,
    int daysLeft) {}
