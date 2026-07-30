package com.pravoos.ai.shared.event;

import java.util.UUID;

public record LawyerDigestKafkaPayload(
    UUID lawyerId,
    String digestDate,
    int tasksTodayCount,
    int upcomingDeadlinesCount,
    int unpaidInvoicesCount,
    String unpaidInvoicesTotalFormatted) {}
