package com.pravoos.ai.core.internal.model.mongo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "conversations")
public class Conversation {

  @Id private String id;

  private UUID lawyerId;

  private String title;

  private UUID caseId;

  private UUID documentId;

  private LocalDateTime createdAt;

  public Conversation() {}

  public Conversation(UUID lawyerId, String title) {
    this(lawyerId, title, null, null);
  }

  public Conversation(UUID lawyerId, String title, UUID caseId, UUID documentId) {
    this.lawyerId = lawyerId;
    this.title = title;
    this.caseId = caseId;
    this.documentId = documentId;
    this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public String getId() {
    return id;
  }

  public UUID getLawyerId() {
    return lawyerId;
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
}
