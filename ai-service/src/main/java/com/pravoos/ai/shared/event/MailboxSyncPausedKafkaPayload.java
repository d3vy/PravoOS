package com.pravoos.ai.shared.event;

import java.util.UUID;

public record MailboxSyncPausedKafkaPayload(
    UUID mailboxId, UUID lawyerId, String emailAddress, String lastError, int failures) {}
