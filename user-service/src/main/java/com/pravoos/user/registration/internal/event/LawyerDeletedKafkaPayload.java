package com.pravoos.user.registration.internal.event;

import java.util.Map;
import java.util.UUID;

public record LawyerDeletedKafkaPayload(UUID userId, Map<UUID, UUID> orgCaseOwners) {}
