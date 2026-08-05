package com.pravoos.ai.shared.mail;

import java.time.LocalDateTime;
import java.util.List;

public record FetchedEmail(
    String messageId,
    long uid,
    String subject,
    String fromAddress,
    List<String> toAddresses,
    List<String> ccAddresses,
    String bodyText,
    LocalDateTime sentAt,
    int attachmentCount,
    String inReplyTo,
    List<String> references) {

  public FetchedEmail {
    toAddresses = toAddresses == null ? List.of() : List.copyOf(toAddresses);
    ccAddresses = ccAddresses == null ? List.of() : List.copyOf(ccAddresses);
    references = references == null ? List.of() : List.copyOf(references);
  }

  public boolean hasAttachments() {
    return attachmentCount > 0;
  }
}
