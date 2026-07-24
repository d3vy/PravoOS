package com.pravoos.ai.practice.internal.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pending_lawyer_purge")
public class PendingLawyerPurge {

  @Id
  @Column(name = "lawyer_id")
  private UUID lawyerId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "last_attempt_at")
  private LocalDateTime lastAttemptAt;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error")
  private String lastError;

  protected PendingLawyerPurge() {}

  public PendingLawyerPurge(UUID lawyerId) {
    this.lawyerId = lawyerId;
  }

  public UUID getLawyerId() {
    return lawyerId;
  }

  public void recordFailedAttempt(String error) {
    this.attempts++;
    this.lastAttemptAt = LocalDateTime.now();
    this.lastError = error;
  }
}
