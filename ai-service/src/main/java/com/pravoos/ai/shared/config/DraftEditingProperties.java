package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "draft-editing")
public record DraftEditingProperties(int maxContentChars, int maxRefineChars) {}
