package com.pravoos.ai.event;

import java.util.Map;
import java.util.UUID;

public record LawyerDeletedKafkaPayload(UUID userId, Map<UUID, UUID> orgCaseOwners) {}
