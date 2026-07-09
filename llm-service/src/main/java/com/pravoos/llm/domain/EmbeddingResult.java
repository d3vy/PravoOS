package com.pravoos.llm.domain;

import java.util.List;

public record EmbeddingResult(List<float[]> embeddings, long totalTokens) {}
