package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user-service")
public record UserServiceProperties(String baseUrl, String internalSecret) {}
