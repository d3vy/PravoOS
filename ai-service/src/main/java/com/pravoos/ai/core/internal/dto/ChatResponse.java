package com.pravoos.ai.core.internal.dto;

import java.util.List;

public record ChatResponse(
    String conversationId,
    String messageId,
    String answer,
    List<String> sources,
    List<String> followUps) {}
