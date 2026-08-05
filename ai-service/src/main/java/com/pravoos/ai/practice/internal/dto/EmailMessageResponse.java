package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import com.pravoos.ai.shared.model.enums.EmailDirection;
import com.pravoos.ai.shared.model.enums.EmailLinkSource;
import java.time.LocalDateTime;
import java.util.UUID;

public record EmailMessageResponse(
    UUID id,
    UUID mailboxId,
    EmailDirection direction,
    String fromAddress,
    String toAddresses,
    String ccAddresses,
    String subject,
    String bodyText,
    LocalDateTime sentAt,
    boolean hasAttachments,
    int attachmentCount,
    UUID caseId,
    UUID clientId,
    EmailLinkSource linkSource,
    String linkSourceName,
    LocalDateTime linkedAt) {

  public static EmailMessageResponse from(EmailMessage message) {
    return new EmailMessageResponse(
        message.getId(),
        message.getMailboxId(),
        message.getDirection(),
        message.getFromAddress(),
        message.getToAddresses(),
        message.getCcAddresses(),
        message.getSubject(),
        message.getBodyText(),
        message.getSentAt(),
        message.isHasAttachments(),
        message.getAttachmentCount(),
        message.getCaseId(),
        message.getClientId(),
        message.getLinkSource(),
        message.getLinkSource() == null ? null : message.getLinkSource().getDisplayName(),
        message.getLinkedAt());
  }
}
