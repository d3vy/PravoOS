package com.pravoos.user.billing.internal.dto;

import com.pravoos.user.billing.internal.model.enums.PaymentStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    String planCode,
    long amountKopecks,
    PaymentStatus status,
    String confirmationUrl,
    LocalDateTime paidAt,
    LocalDateTime createdAt) {}
