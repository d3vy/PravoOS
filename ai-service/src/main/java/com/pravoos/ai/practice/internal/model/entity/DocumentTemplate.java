package com.pravoos.ai.practice.internal.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "document_templates")
public class DocumentTemplate {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID lawyerId;

  @Column(nullable = false, length = 300)
  private String name;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public UUID getId() {
    return id;
  }

  public UUID getLawyerId() {
    return lawyerId;
  }

  public void setLawyerId(UUID lawyerId) {
    this.lawyerId = lawyerId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
