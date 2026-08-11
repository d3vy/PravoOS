package com.pravoos.ai.shared.mail;

import java.util.List;

public record MailboxFetchResult(
    long uidValidity,
    long lastSeenUid,
    boolean reindexed,
    List<FetchedEmail> messages,
    Long unreadableUid) {

  public MailboxFetchResult {
    messages = messages == null ? List.of() : List.copyOf(messages);
  }

  public MailboxFetchResult(
      long uidValidity, long lastSeenUid, boolean reindexed, List<FetchedEmail> messages) {
    this(uidValidity, lastSeenUid, reindexed, messages, null);
  }

  public static MailboxFetchResult empty(long uidValidity, long lastSeenUid, boolean reindexed) {
    return new MailboxFetchResult(uidValidity, lastSeenUid, reindexed, List.of(), null);
  }

  public boolean hasUnreadableUid() {
    return unreadableUid != null;
  }
}
