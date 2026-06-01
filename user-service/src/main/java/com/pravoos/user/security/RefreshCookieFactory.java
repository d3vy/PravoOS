package com.pravoos.user.security;

import com.pravoos.user.config.JwtProperties;
import com.pravoos.user.config.RefreshCookieProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final RefreshCookieProperties cookieProperties;
    private final Duration maxAge;

    public RefreshCookieFactory(RefreshCookieProperties cookieProperties, JwtProperties jwtProperties) {
        this.cookieProperties = cookieProperties;
        this.maxAge = Duration.ofMillis(jwtProperties.refreshExpirationMs());
    }

    public ResponseCookie create(String rawToken) {
        return baseBuilder(rawToken)
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie clear() {
        return baseBuilder("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(COOKIE_PATH);
    }
}
