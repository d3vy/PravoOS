package com.pravoos.notification.event;

import java.util.UUID;

public record MailboxSyncPausedKafkaPayload(
    UUID mailboxId,
    UUID lawyerId,
    String emailAddress,
    String lastError,
    int failures,
    String pausedAt) {}
