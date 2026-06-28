package com.pravoos.user.event;

import java.util.UUID;

public record ApplicationSubmittedKafkaPayload(
        UUID applicationId
) {}
