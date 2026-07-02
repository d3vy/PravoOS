package com.pravoos.ai.llm;

import java.util.List;

public record EmbeddingResult(List<float[]> embeddings, long totalTokens) {}
