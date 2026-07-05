package com.pravoos.user.collaboration.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(String botUsername, Duration linkCodeTtl) {}
