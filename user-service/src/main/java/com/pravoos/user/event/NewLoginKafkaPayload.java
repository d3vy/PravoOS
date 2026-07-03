package com.pravoos.user.event;

import java.util.UUID;

public record NewLoginKafkaPayload(
        UUID userId,
        String ipAddress,
        String userAgent,
        String occurredAt
) {}
