package com.pravoos.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.crypto")
public record FileCryptoProperties(
        String key
) {
    public boolean hasKey() {
        return key != null && !key.isBlank();
    }
}
