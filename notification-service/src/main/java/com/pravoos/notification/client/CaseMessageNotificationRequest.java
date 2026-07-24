package com.pravoos.notification.client;

import java.util.UUID;

public record CaseMessageNotificationRequest(
    UUID caseId,
    String caseTitle,
    String authorRole,
    UUID recipientLawyerId,
    UUID recipientClientId,
    String preview) {}
