package com.pravoos.ai.model.dto;

import java.time.LocalDateTime;

public record ConversationResponse(
        String id,
        String title,
        LocalDateTime createdAt
) {}
