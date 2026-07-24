package com.pravoos.user.identity.internal.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "user_mfa")
public class UserMfa {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "secret", nullable = false)
  private String secret;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "confirmed_at")
  private LocalDateTime confirmedAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getConfirmedAt() {
    return confirmedAt;
  }

  public void setConfirmedAt(LocalDateTime confirmedAt) {
    this.confirmedAt = confirmedAt;
  }
}
