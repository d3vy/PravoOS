package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.EmailAttachment;
import com.pravoos.ai.shared.model.enums.EmailAttachmentStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record EmailAttachmentResponse(
    UUID id,
    int partIndex,
    String fileName,
    String contentType,
    long sizeBytes,
    EmailAttachmentStatus status,
    String reason,
    UUID documentId,
    LocalDateTime importedAt) {

  public static EmailAttachmentResponse from(EmailAttachment attachment) {
    return new EmailAttachmentResponse(
        attachment.getId(),
        attachment.getPartIndex(),
        attachment.getFileName(),
        attachment.getContentType(),
        attachment.getSizeBytes(),
        attachment.getStatus(),
        attachment.getReason(),
        attachment.getDocumentId(),
        attachment.getImportedAt());
  }
}
