package com.pravoos.ai.core.internal.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_action_proposal")
public class AiActionProposal {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private UUID orgId;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 64)
  private String conversationId;

  @Column(length = 64)
  private String messageId;

  @Column(nullable = false, length = 100)
  private String toolName;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private JsonNode arguments;

  @Column(nullable = false, length = 500)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AiActionProposalStatus status;

  @Column(columnDefinition = "TEXT")
  private String result;

  @Column(length = 1000)
  private String failureReason;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  private LocalDateTime decidedAt;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  protected AiActionProposal() {}

  public AiActionProposal(
      UUID orgId,
      UUID userId,
      String conversationId,
      String toolName,
      JsonNode arguments,
      String title,
      LocalDateTime createdAt,
      LocalDateTime expiresAt) {
    this.orgId = orgId;
    this.userId = userId;
    this.conversationId = conversationId;
    this.toolName = toolName;
    this.arguments = arguments;
    this.title = title;
    this.status = AiActionProposalStatus.PENDING;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
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

  public String getConversationId() {
    return conversationId;
  }

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  public String getToolName() {
    return toolName;
  }

  public JsonNode getArguments() {
    return arguments;
  }

  public String getTitle() {
    return title;
  }

  public AiActionProposalStatus getStatus() {
    return status;
  }

  public void setStatus(AiActionProposalStatus status) {
    this.status = status;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(LocalDateTime decidedAt) {
    this.decidedAt = decidedAt;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }
}
