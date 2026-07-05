package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arbitr")
public record ArbitrProperties(
        Api api
) {
    public record Api(
            String baseUrl,
            String key
    ) {
        public boolean hasKey() {
            return key != null && !key.isBlank();
        }
    }
}
