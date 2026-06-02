package com.pravoos.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "telegram.bot")
public record TelegramBotProperties(
        String token,
        String username,
        List<String> adminChatIds
) {}
