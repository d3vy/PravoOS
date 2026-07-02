package com.pravoos.ai.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAiEmbeddingResponse(
        List<EmbeddingData> data,
        Usage usage
) {
    public record EmbeddingData(float[] embedding) {}

    public record Usage(@JsonProperty("total_tokens") long totalTokens) {}

    public float[] firstEmbedding() {
        if (data == null || data.isEmpty()) return new float[0];
        return data.get(0).embedding();
    }

    public List<float[]> allEmbeddings() {
        if (data == null) return List.of();
        return data.stream().map(EmbeddingData::embedding).toList();
    }

    public long totalTokens() {
        return usage == null ? 0L : usage.totalTokens();
    }
}
