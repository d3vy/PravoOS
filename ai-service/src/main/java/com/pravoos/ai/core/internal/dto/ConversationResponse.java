package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.core.internal.model.mongo.Conversation;
import java.time.LocalDateTime;

public record ConversationResponse(
    String id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {

  public static ConversationResponse from(Conversation conversation) {
    return new ConversationResponse(
        conversation.getId(),
        conversation.getTitle(),
        conversation.getCreatedAt(),
        conversation.getUpdatedAt() != null
            ? conversation.getUpdatedAt()
            : conversation.getCreatedAt());
  }
}
