package com.pravoos.ai.practice.internal.event;

import java.util.UUID;

public record MailboxSyncPausedPayload(
    UUID mailboxId,
    UUID lawyerId,
    String emailAddress,
    String lastError,
    int failures,
    String pausedAt) {}
