package com.pravoos.notification.event;

import java.util.UUID;

public record InvoicePaidKafkaPayload(
    UUID invoiceId,
    UUID lawyerId,
    String invoiceNumber,
    String clientName,
    String totalFormatted) {}
