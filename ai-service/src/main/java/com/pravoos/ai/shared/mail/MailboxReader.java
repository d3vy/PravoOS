package com.pravoos.ai.shared.mail;

import java.util.List;

public interface MailboxReader {

  void verifyConnection(MailboxCredentials credentials);

  MailboxFetchResult fetchMessages(
      MailboxCredentials credentials, MailboxSyncCursor cursor, int maxMessages);

  List<FetchedAttachment> fetchAttachments(
      MailboxCredentials credentials, MailboxSyncCursor cursor, long uid);
}
