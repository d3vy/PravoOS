package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.MailboxStatus;
import java.util.UUID;

public record MailSyncResult(
    UUID mailboxId,
    boolean success,
    MailboxStatus status,
    int fetched,
    int saved,
    boolean reindexed,
    String error) {

  public static MailSyncResult ok(UUID mailboxId, int fetched, int saved, boolean reindexed) {
    return new MailSyncResult(mailboxId, true, MailboxStatus.OK, fetched, saved, reindexed, null);
  }

  public static MailSyncResult failed(UUID mailboxId, String error) {
    return new MailSyncResult(mailboxId, false, MailboxStatus.ERROR, 0, 0, false, error);
  }

  public static MailSyncResult alreadyRunning(UUID mailboxId, MailboxStatus status) {
    return new MailSyncResult(
        mailboxId, true, status, 0, 0, false, "Синхронизация этого ящика уже выполняется");
  }
}
