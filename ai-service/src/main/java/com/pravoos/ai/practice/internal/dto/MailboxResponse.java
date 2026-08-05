package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.shared.model.enums.MailboxStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record MailboxResponse(
    UUID id,
    String emailAddress,
    String imapHost,
    int imapPort,
    boolean imapSsl,
    String folder,
    boolean syncEnabled,
    MailboxStatus status,
    String lastError,
    LocalDateTime lastSyncAt,
    LocalDateTime createdAt) {

  public static MailboxResponse from(Mailbox mailbox) {
    return new MailboxResponse(
        mailbox.getId(),
        mailbox.getEmailAddress(),
        mailbox.getImapHost(),
        mailbox.getImapPort(),
        mailbox.isImapSsl(),
        mailbox.getFolder(),
        mailbox.isSyncEnabled(),
        mailbox.getStatus(),
        mailbox.getLastError(),
        mailbox.getLastSyncAt(),
        mailbox.getCreatedAt());
  }
}
