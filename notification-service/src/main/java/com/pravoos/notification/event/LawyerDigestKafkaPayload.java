package com.pravoos.notification.event;

import java.util.UUID;

public record LawyerDigestKafkaPayload(
    UUID lawyerId,
    String digestDate,
    int tasksTodayCount,
    int upcomingDeadlinesCount,
    int unpaidInvoicesCount,
    String unpaidInvoicesTotalFormatted) {}
