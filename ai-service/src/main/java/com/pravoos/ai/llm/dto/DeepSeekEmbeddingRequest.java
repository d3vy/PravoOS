package com.pravoos.ai.llm.dto;

public record DeepSeekEmbeddingRequest(
        String model,
        String input
) {}
