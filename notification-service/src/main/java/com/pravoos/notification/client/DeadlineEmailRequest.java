package com.pravoos.notification.client;

import java.util.UUID;

public record DeadlineEmailRequest(
        UUID lawyerId,
        UUID caseId,
        String caseTitle,
        String deadlineTypeName,
        String deadlineDate,
        int daysLeft
) {}
