package com.pravoos.notification.event;

import java.util.UUID;

public record ApplicationSubmittedKafkaPayload(
    UUID applicationId, String fullName, String email, String specialization) {}
