package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.enums.MessageRole;
import com.pravoos.ai.model.mongo.Message;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        MessageRole role,
        String content,
        List<String> sources,
        LocalDateTime createdAt
) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getRole(),
                message.getContent(),
                message.getSources() != null ? message.getSources() : List.of(),
                message.getCreatedAt()
        );
    }
}
