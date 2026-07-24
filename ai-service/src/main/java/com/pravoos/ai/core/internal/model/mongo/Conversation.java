package com.pravoos.ai.core.internal.model.mongo;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "conversations")
public class Conversation {

  @Id private String id;

  private UUID lawyerId;

  private String title;

  private LocalDateTime createdAt;

  public Conversation() {}

  public Conversation(UUID lawyerId, String title) {
    this.lawyerId = lawyerId;
    this.title = title;
    this.createdAt = LocalDateTime.now();
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

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
