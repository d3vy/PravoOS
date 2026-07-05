package com.pravoos.notification.event;

import java.util.UUID;

public record CaseMessageCreatedKafkaPayload(
        UUID caseId,
        String caseTitle,
        UUID messageId,
        String authorRole,
        UUID authorUserId,
        UUID recipientLawyerId,
        UUID recipientClientId,
        String preview
) {}
