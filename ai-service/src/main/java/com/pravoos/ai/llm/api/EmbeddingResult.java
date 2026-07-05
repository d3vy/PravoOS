package com.pravoos.ai.llm.api;

import java.util.List;

public record EmbeddingResult(List<float[]> embeddings, long totalTokens) {}
