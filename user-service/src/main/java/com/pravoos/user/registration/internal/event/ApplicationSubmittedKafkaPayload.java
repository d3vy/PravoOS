package com.pravoos.user.registration.internal.event;

import java.util.UUID;

public record ApplicationSubmittedKafkaPayload(
        UUID applicationId
) {}
