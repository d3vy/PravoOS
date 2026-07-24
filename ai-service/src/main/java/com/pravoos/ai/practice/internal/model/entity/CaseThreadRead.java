package com.pravoos.ai.practice.internal.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "case_thread_reads")
public class CaseThreadRead {

  @Embeddable
  public static class Id implements Serializable {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected Id() {}

    public Id(UUID caseId, UUID userId) {
      this.caseId = caseId;
      this.userId = userId;
    }

    public UUID getCaseId() {
      return caseId;
    }

    public UUID getUserId() {
      return userId;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof Id otherId)) {
        return false;
      }
      return Objects.equals(caseId, otherId.caseId) && Objects.equals(userId, otherId.userId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(caseId, userId);
    }
  }

  @EmbeddedId private Id id;

  @Column(name = "last_read_at", nullable = false)
  private LocalDateTime lastReadAt;

  protected CaseThreadRead() {}

  public CaseThreadRead(UUID caseId, UUID userId, LocalDateTime lastReadAt) {
    this.id = new Id(caseId, userId);
    this.lastReadAt = lastReadAt;
  }

  public Id getId() {
    return id;
  }

  public LocalDateTime getLastReadAt() {
    return lastReadAt;
  }

  public void setLastReadAt(LocalDateTime lastReadAt) {
    this.lastReadAt = lastReadAt;
  }
}
