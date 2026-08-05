package com.pravoos.ai.practice.internal.model.entity;

import com.pravoos.ai.shared.model.enums.EmailAttachmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "email_attachments")
public class EmailAttachment {

  private static final int MAX_FILE_NAME_LENGTH = 255;
  private static final int MAX_CONTENT_TYPE_LENGTH = 255;
  private static final int MAX_REASON_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "email_message_id", nullable = false)
  private UUID emailMessageId;

  @Column(name = "part_index", nullable = false)
  private int partIndex;

  @Column(name = "file_name", nullable = false, length = MAX_FILE_NAME_LENGTH)
  private String fileName;

  @Column(name = "content_type", length = MAX_CONTENT_TYPE_LENGTH)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EmailAttachmentStatus status;

  @Column(length = MAX_REASON_LENGTH)
  private String reason;

  @Column(name = "document_id")
  private UUID documentId;

  @Column(name = "imported_at")
  private LocalDateTime importedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected EmailAttachment() {}

  public EmailAttachment(UUID emailMessageId, int partIndex, String fileName, String contentType) {
    this.emailMessageId = emailMessageId;
    this.partIndex = partIndex;
    this.fileName = truncate(fileName, MAX_FILE_NAME_LENGTH);
    this.contentType = truncate(contentType, MAX_CONTENT_TYPE_LENGTH);
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public void markImported(UUID documentId) {
    this.status = EmailAttachmentStatus.IMPORTED;
    this.documentId = documentId;
    this.reason = null;
    this.importedAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public void markRejected(String reason) {
    markUnsuccessful(EmailAttachmentStatus.REJECTED, reason);
  }

  public void markSkipped(String reason) {
    markUnsuccessful(EmailAttachmentStatus.SKIPPED, reason);
  }

  public void markFailed(String reason) {
    markUnsuccessful(EmailAttachmentStatus.FAILED, reason);
  }

  private void markUnsuccessful(EmailAttachmentStatus status, String reason) {
    this.status = status;
    this.reason = truncate(reason, MAX_REASON_LENGTH);
    this.documentId = null;
    this.importedAt = null;
  }

  public boolean isImported() {
    return status == EmailAttachmentStatus.IMPORTED;
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  public UUID getId() {
    return id;
  }

  public UUID getEmailMessageId() {
    return emailMessageId;
  }

  public int getPartIndex() {
    return partIndex;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = truncate(fileName, MAX_FILE_NAME_LENGTH);
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = truncate(contentType, MAX_CONTENT_TYPE_LENGTH);
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  public EmailAttachmentStatus getStatus() {
    return status;
  }

  public String getReason() {
    return reason;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public LocalDateTime getImportedAt() {
    return importedAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
