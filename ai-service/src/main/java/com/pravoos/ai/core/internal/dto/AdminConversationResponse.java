package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.core.internal.model.mongo.Conversation;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminConversationResponse(
    String id,
    UUID lawyerId,
    UUID orgId,
    String title,
    UUID caseId,
    UUID documentId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AdminConversationResponse from(Conversation conversation) {
    return new AdminConversationResponse(
        conversation.getId(),
        conversation.getLawyerId(),
        conversation.getOrgId(),
        conversation.getTitle(),
        conversation.getCaseId(),
        conversation.getDocumentId(),
        conversation.getCreatedAt(),
        conversation.getUpdatedAt() != null
            ? conversation.getUpdatedAt()
            : conversation.getCreatedAt());
  }
}
