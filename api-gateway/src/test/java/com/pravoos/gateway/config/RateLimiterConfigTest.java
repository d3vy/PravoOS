package com.pravoos.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterConfigTest {

    private final KeyResolver resolver = new RateLimiterConfig().ipKeyResolver();

    @Test
    void usesLastForwardedHop_notClientSpoofedLeftmost() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/login")
                .header("X-Forwarded-For", "9.9.9.9, 8.8.8.8, 203.0.113.7")
                .build();

        String key = resolver.resolve(MockServerWebExchange.from(request)).block();

        assertThat(key).isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToRemoteAddress_whenNoForwardedHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/login")
                .remoteAddress(new InetSocketAddress("198.51.100.5", 12345))
                .build();

        String key = resolver.resolve(MockServerWebExchange.from(request)).block();

        assertThat(key).isEqualTo("198.51.100.5");
    }
}
