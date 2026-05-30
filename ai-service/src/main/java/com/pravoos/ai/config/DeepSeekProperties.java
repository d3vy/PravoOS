package com.pravoos.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.deepseek")
public record DeepSeekProperties(
        String apiKey,
        String baseUrl,
        String model,
        String embeddingModel,
        int embeddingDimensions,
        int maxTokens
) {}
