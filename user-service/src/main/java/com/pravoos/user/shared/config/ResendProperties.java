package com.pravoos.user.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "resend")
public record ResendProperties(
    String apiKey,
    String from,
    String frontendBaseUrl,
    int verificationExpiryHours,
    int passwordResetExpiryHours,
    int maxEmailsPerHour) {}
