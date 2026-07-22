package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.upload-guard")
public record UploadGuardProperties(
        long maxFileBytes,
        long maxUncompressedBytes,
        int maxCompressionRatio,
        int maxArchiveEntries,
        boolean blockActiveContent
) {}
