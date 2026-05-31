package com.pravoos.ai.llm.dto;

public record OpenAiEmbeddingRequest(
        String model,
        String input
) {}
