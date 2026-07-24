package com.pravoos.notification.event;

import java.util.UUID;

public record NewLoginKafkaPayload(
    UUID userId,
    String ipAddress,
    String userAgent,
    String occurredAt,
    boolean telegramEnabled,
    boolean pushEnabled) {}
