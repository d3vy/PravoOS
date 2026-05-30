package com.pravoos.ai.service;

import com.pravoos.ai.config.DeepSeekProperties;
import com.pravoos.ai.exception.LlmException;
import com.pravoos.ai.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final LlmClient llmClient;
    private final int expectedDimensions;

    public EmbeddingService(LlmClient llmClient, DeepSeekProperties properties) {
        this.llmClient = llmClient;
        this.expectedDimensions = properties.embeddingDimensions();
    }

    public float[] embed(String text) {
        float[] embedding = llmClient.embed(text);
        if (embedding.length != expectedDimensions) {
            log.error("Embedding dimension mismatch: expected {}, got {}", expectedDimensions, embedding.length);
            throw new LlmException(
                    "Embedding dimension mismatch: expected " + expectedDimensions + ", got " + embedding.length);
        }
        return embedding;
    }
}
