package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "contract-review")
public record ContractReviewProperties(int maxInputChars, int maxFindings) {}
