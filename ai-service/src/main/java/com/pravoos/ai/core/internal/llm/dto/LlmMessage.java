package com.pravoos.ai.core.internal.llm.dto;

public record LlmMessage(
        String role,
        String content
) {}
