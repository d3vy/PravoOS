package com.pravoos.ai.practice.internal.model.entity;

import com.pravoos.ai.practice.internal.model.SavedViewScope;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "saved_views")
@SQLRestriction("deleted_at IS NULL")
public class SavedView {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID lawyerId;

  private UUID orgId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SavedViewScope scope;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String config;

  @Column(nullable = false)
  private boolean sharedWithTeam;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now(ZoneOffset.UTC);
    updatedAt = createdAt;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now(ZoneOffset.UTC);
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

  public UUID getOrgId() {
    return orgId;
  }

  public void setOrgId(UUID orgId) {
    this.orgId = orgId;
  }

  public SavedViewScope getScope() {
    return scope;
  }

  public void setScope(SavedViewScope scope) {
    this.scope = scope;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getConfig() {
    return config;
  }

  public void setConfig(String config) {
    this.config = config;
  }

  public boolean isSharedWithTeam() {
    return sharedWithTeam;
  }

  public void setSharedWithTeam(boolean sharedWithTeam) {
    this.sharedWithTeam = sharedWithTeam;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
