package com.pravoos.ai.core.internal.model.mongo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "conversations")
public class Conversation {

  @Id private String id;

  private UUID lawyerId;

  private UUID orgId;

  private String title;

  private UUID caseId;

  private UUID documentId;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  private LocalDateTime deletedAt;

  public Conversation() {}

  public Conversation(UUID lawyerId, String title) {
    this(lawyerId, null, title, null, null);
  }

  public Conversation(UUID lawyerId, UUID orgId, String title, UUID caseId, UUID documentId) {
    this.id = new ObjectId().toString();
    this.lawyerId = lawyerId;
    this.orgId = orgId;
    this.title = title;
    this.caseId = caseId;
    this.documentId = documentId;
    this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    this.updatedAt = this.createdAt;
  }

  public String getId() {
    return id;
  }

  public UUID getLawyerId() {
    return lawyerId;
  }

  public UUID getOrgId() {
    return orgId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public UUID getCaseId() {
    return caseId;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }
}
