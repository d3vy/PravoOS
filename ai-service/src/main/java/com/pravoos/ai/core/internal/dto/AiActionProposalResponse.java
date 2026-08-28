package com.pravoos.ai.core.internal.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pravoos.ai.core.internal.model.entity.AiActionProposal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AiActionProposalResponse(
    UUID id,
    String conversationId,
    String messageId,
    String toolName,
    String title,
    String status,
    JsonNode arguments,
    String result,
    String failureReason,
    LocalDateTime createdAt,
    LocalDateTime expiresAt) {

  public static AiActionProposalResponse from(AiActionProposal proposal) {
    return new AiActionProposalResponse(
        proposal.getId(),
        proposal.getConversationId(),
        proposal.getMessageId(),
        proposal.getToolName(),
        proposal.getTitle(),
        proposal.getStatus().name(),
        proposal.getArguments(),
        proposal.getResult(),
        proposal.getFailureReason(),
        proposal.getCreatedAt(),
        proposal.getExpiresAt());
  }
}
