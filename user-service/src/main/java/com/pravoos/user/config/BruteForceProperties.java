package com.pravoos.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth.brute-force")
public record BruteForceProperties(
        int maxAttempts,
        Duration lockoutDuration,
        Duration attemptWindow
) {}
