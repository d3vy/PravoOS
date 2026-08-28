package com.pravoos.ai.core.internal.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_trusted_tool")
public class AiTrustedTool {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private UUID orgId;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 100)
  private String toolName;

  @Column(nullable = false)
  private LocalDateTime grantedAt;

  protected AiTrustedTool() {}

  public AiTrustedTool(UUID orgId, UUID userId, String toolName, LocalDateTime grantedAt) {
    this.orgId = orgId;
    this.userId = userId;
    this.toolName = toolName;
    this.grantedAt = grantedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrgId() {
    return orgId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getToolName() {
    return toolName;
  }

  public LocalDateTime getGrantedAt() {
    return grantedAt;
  }
}
