package com.pravoos.ai.practice.internal.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "case_draft_versions")
public class CaseDraftVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID draftId;

  @Column(nullable = false)
  private int versionNo;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(length = 500)
  private String note;

  @Column(nullable = false)
  private UUID createdBy;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public UUID getId() {
    return id;
  }

  public UUID getDraftId() {
    return draftId;
  }

  public void setDraftId(UUID draftId) {
    this.draftId = draftId;
  }

  public int getVersionNo() {
    return versionNo;
  }

  public void setVersionNo(int versionNo) {
    this.versionNo = versionNo;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
