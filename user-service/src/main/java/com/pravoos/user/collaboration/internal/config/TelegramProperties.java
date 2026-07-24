package com.pravoos.user.collaboration.internal.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(String botUsername, Duration linkCodeTtl) {}
