package com.pravoos.ai.llm.dto;

import java.util.List;

public record DeepSeekEmbeddingResponse(
        List<EmbeddingData> data
) {
    public record EmbeddingData(float[] embedding) {}

    public float[] firstEmbedding() {
        if (data == null || data.isEmpty()) return new float[0];
        return data.get(0).embedding();
    }
}
