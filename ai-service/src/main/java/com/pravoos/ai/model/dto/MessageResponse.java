package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.enums.MessageRole;
import com.pravoos.ai.model.mongo.Message;

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
