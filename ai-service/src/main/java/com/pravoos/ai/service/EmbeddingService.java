package com.pravoos.ai.service;

import com.pravoos.ai.config.OpenAiProperties;
import com.pravoos.ai.exception.LlmException;
import com.pravoos.ai.llm.LlmClient;
import com.pravoos.ai.model.entity.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final LlmClient llmClient;
    private final int expectedDimensions;

    public EmbeddingService(LlmClient llmClient, OpenAiProperties properties) {
        this.llmClient = llmClient;
        this.expectedDimensions = properties.embeddingDimensions();
        if (expectedDimensions != DocumentChunk.EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException(
                    "Configured embedding dimensions (" + expectedDimensions + ") do not match the database "
                            + "vector column dimension (" + DocumentChunk.EMBEDDING_DIMENSIONS + "). "
                            + "Either set OPENAI_EMBEDDING_DIMENSIONS=" + DocumentChunk.EMBEDDING_DIMENSIONS
                            + " or add a migration that alters the document_chunks.embedding column.");
        }
    }

    public float[] embed(String text) {
        return validateDimensions(llmClient.embed(text));
    }

    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> embeddings = llmClient.embedBatch(texts);
        embeddings.forEach(this::validateDimensions);
        return embeddings;
    }

    private float[] validateDimensions(float[] embedding) {
        if (embedding.length != expectedDimensions) {
            log.error("Embedding dimension mismatch: expected {}, got {}", expectedDimensions, embedding.length);
            throw new LlmException(
                    "Embedding dimension mismatch: expected " + expectedDimensions + ", got " + embedding.length);
        }
        return embedding;
    }
}
