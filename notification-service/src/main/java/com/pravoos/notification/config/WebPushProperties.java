package com.pravoos.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.push.vapid")
public record WebPushProperties(
        String publicKey,
        String privateKey,
        String subject
) {

    private static final String DEFAULT_SUBJECT = "mailto:support@pravoos.ru";

    public WebPushProperties {
        subject = subject == null || subject.isBlank() ? DEFAULT_SUBJECT : subject;
    }

    public boolean configured() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }
}
