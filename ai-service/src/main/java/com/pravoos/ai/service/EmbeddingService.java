package com.pravoos.ai.service;

import com.pravoos.ai.llm.LlmClient;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private final LlmClient llmClient;

    public EmbeddingService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public float[] embed(String text) {
        return llmClient.embed(text);
    }
}
