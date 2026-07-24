package com.pravoos.user.identity.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "test-lawyer")
public record TestLawyerProperties(
    boolean enabled,
    String email,
    String password,
    String fullName,
    String specialization,
    String phone) {}
