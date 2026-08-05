package com.pravoos.ai.shared.mail;

public record MailboxSyncCursor(Long uidValidity, Long lastSeenUid) {

  public static MailboxSyncCursor initial() {
    return new MailboxSyncCursor(null, null);
  }

  public boolean matches(long serverUidValidity) {
    return uidValidity != null && uidValidity == serverUidValidity;
  }

  public long startUid(long serverUidValidity) {
    if (!matches(serverUidValidity) || lastSeenUid == null) {
      return 1L;
    }
    return lastSeenUid + 1;
  }
}
