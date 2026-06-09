package com.pravoos.ai.llm.dto;

import java.util.List;

public record OpenAiEmbeddingRequest(
        String model,
        List<String> input
) {}
