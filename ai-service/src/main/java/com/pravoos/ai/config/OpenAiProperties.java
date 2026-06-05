package com.pravoos.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl,
        String model,
        String guardModel,
        String embeddingModel,
        int embeddingDimensions,
        int maxTokens
) {}
