package com.pravoos.user.push.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.push.vapid")
public record VapidProperties(
        String publicKey
) {

    public boolean configured() {
        return publicKey != null && !publicKey.isBlank();
    }
}
