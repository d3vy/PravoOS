package com.pravoos.ai.llm.dto;

import java.util.List;

public record OpenAiEmbeddingResponse(
        List<EmbeddingData> data
) {
    public record EmbeddingData(float[] embedding) {}

    public float[] firstEmbedding() {
        if (data == null || data.isEmpty()) return new float[0];
        return data.get(0).embedding();
    }

    public List<float[]> allEmbeddings() {
        if (data == null) return List.of();
        return data.stream().map(EmbeddingData::embedding).toList();
    }
}
