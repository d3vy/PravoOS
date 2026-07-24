package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document")
public record DocumentProperties(
    String storagePath,
    int chunkSize,
    int chunkOverlap,
    int topKResults,
    int contextMaxChars,
    int maxPerCase,
    int maxPerLawyer,
    long maxTotalBytesPerLawyer,
    int uploadRatePerMinute) {}
