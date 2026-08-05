package com.pravoos.notification.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.UUID;

public record CaseHearingUpdatedKafkaPayload(
    UUID caseId,
    UUID lawyerId,
    String caseTitle,
    @JsonAlias("arbitrCaseNumber") String courtCaseNumber,
    String previousHearingDate,
    String newHearingDate) {}
