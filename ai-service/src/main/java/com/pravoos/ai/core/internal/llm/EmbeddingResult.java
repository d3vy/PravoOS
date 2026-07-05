package com.pravoos.ai.core.internal.llm;

import java.util.List;

public record EmbeddingResult(List<float[]> embeddings, long totalTokens) {}
