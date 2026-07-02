package com.pravoos.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.password-policy")
public record PasswordPolicyProperties(
        boolean hibpEnabled,
        Duration hibpTimeout,
        boolean hibpFailOpen
) {
    public PasswordPolicyProperties {
        if (hibpTimeout == null) {
            hibpTimeout = Duration.ofSeconds(3);
        }
    }
}
