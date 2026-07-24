package com.pravoos.user.identity.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.refresh-cookie")
public record RefreshCookieProperties(boolean secure, String sameSite) {}
