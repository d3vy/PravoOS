package com.pravoos.ai.shared.event;

import java.util.Map;
import java.util.UUID;

public record LawyerDeletedKafkaPayload(UUID userId, Map<UUID, UUID> orgCaseOwners) {}
