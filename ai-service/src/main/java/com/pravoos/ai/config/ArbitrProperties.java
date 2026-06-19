package com.pravoos.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arbitr")
public record ArbitrProperties(
        Api api
) {
    public record Api(
            String baseUrl,
            String token
    ) {
        public boolean hasToken() {
            return token != null && !token.isBlank();
        }
    }
}
