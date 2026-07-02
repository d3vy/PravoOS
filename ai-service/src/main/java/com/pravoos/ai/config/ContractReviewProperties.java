package com.pravoos.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "contract-review")
public record ContractReviewProperties(
        int maxInputChars,
        int maxFindings
) {}
