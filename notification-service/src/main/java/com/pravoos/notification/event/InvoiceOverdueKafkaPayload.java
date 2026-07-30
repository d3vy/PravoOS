package com.pravoos.notification.event;

import java.util.UUID;

public record InvoiceOverdueKafkaPayload(
    UUID invoiceId,
    UUID lawyerId,
    String invoiceNumber,
    String clientName,
    String totalFormatted,
    int daysOverdue) {}
