package com.pravoos.ai.shared.mail;

import java.util.List;

public record MailboxFetchResult(
    long uidValidity, long lastSeenUid, boolean reindexed, List<FetchedEmail> messages) {

  public MailboxFetchResult {
    messages = messages == null ? List.of() : List.copyOf(messages);
  }

  public static MailboxFetchResult empty(long uidValidity, long lastSeenUid, boolean reindexed) {
    return new MailboxFetchResult(uidValidity, lastSeenUid, reindexed, List.of());
  }
}
