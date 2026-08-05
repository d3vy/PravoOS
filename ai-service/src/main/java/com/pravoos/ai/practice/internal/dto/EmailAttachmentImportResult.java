package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.EmailAttachmentStatus;
import java.util.List;
import java.util.UUID;

public record EmailAttachmentImportResult(
    UUID emailId,
    UUID caseId,
    int imported,
    int rejected,
    int skipped,
    int failed,
    List<EmailAttachmentResponse> attachments) {

  public static EmailAttachmentImportResult of(
      UUID emailId, UUID caseId, List<EmailAttachmentResponse> attachments) {
    return new EmailAttachmentImportResult(
        emailId,
        caseId,
        count(attachments, EmailAttachmentStatus.IMPORTED),
        count(attachments, EmailAttachmentStatus.REJECTED),
        count(attachments, EmailAttachmentStatus.SKIPPED),
        count(attachments, EmailAttachmentStatus.FAILED),
        List.copyOf(attachments));
  }

  private static int count(
      List<EmailAttachmentResponse> attachments, EmailAttachmentStatus status) {
    return (int) attachments.stream().filter(attachment -> attachment.status() == status).count();
  }
}
