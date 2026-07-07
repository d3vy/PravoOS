package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.core.internal.model.mongo.Message;
import com.pravoos.ai.shared.model.enums.MessageRole;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        String id,
        MessageRole role,
        String content,
        List<String> sources,
        Integer rating,
        LocalDateTime createdAt
) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getSources() != null ? message.getSources() : List.of(),
                message.getRating(),
                message.getCreatedAt()
        );
    }
}
