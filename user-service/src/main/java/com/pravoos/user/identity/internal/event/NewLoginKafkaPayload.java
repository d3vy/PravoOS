package com.pravoos.user.identity.internal.event;

import java.util.UUID;

public record NewLoginKafkaPayload(
        UUID userId,
        String ipAddress,
        String userAgent,
        String occurredAt
) {}
