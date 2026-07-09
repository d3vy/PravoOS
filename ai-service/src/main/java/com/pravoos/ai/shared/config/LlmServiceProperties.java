package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.service")
public record LlmServiceProperties(
        String baseUrl,
        String internalSecret,
        int embeddingDimensions
) {}
