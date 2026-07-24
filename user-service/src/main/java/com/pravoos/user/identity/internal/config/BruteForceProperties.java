package com.pravoos.user.identity.internal.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.brute-force")
public record BruteForceProperties(
    int maxAttempts, Duration lockoutDuration, Duration attemptWindow, boolean failOpen) {}
