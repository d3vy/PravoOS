package com.pravoos.user.identity.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String privateKey, String publicKey, long accessExpirationMs, long refreshExpirationMs) {}
