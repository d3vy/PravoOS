package com.pravoos.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.mfa")
public record MfaProperties(
        String issuer,
        boolean adminRequired,
        Duration challengeTtl,
        int maxChallengeAttempts
) {
    public MfaProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "PravoOS";
        }
        if (challengeTtl == null) {
            challengeTtl = Duration.ofMinutes(5);
        }
        if (maxChallengeAttempts <= 0) {
            maxChallengeAttempts = 5;
        }
    }
}
