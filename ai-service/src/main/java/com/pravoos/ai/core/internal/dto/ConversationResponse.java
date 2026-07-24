package com.pravoos.ai.core.internal.dto;

import java.time.LocalDateTime;

public record ConversationResponse(String id, String title, LocalDateTime createdAt) {}
