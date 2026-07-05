package com.pravoos.ai.core.internal.llm.dto;

import java.util.List;

public record OpenAiEmbeddingRequest(
        String model,
        List<String> input
) {}
