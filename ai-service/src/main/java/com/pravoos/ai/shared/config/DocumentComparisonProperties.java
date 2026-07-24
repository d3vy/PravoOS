package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document-comparison")
public record DocumentComparisonProperties(int maxInputChars, int maxChanges, int changeMaxChars) {}
