package com.pravoos.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "citation-check")
public record CitationCheckProperties(
        int maxCitations,
        int maxCourtCaseLookups
) {}
