package com.pravoos.user.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        List<Account> accounts
) {
    public record Account(
            String email,
            String password
    ) {}
}
