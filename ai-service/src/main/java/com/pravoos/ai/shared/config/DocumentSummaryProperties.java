package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.summary")
public record DocumentSummaryProperties(
    boolean enabled,
    int maxInputChars,
    int maxKeyPoints,
    int maxSummaryChars,
    int maxKeyPointChars,
    int maxTokens) {}
