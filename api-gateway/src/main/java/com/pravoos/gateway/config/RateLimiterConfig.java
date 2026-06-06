package com.pravoos.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return Mono.just(forwardedFor.split(",")[0].trim());
            }
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String clientIp = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
            return Mono.just(clientIp);
        };
    }
}
