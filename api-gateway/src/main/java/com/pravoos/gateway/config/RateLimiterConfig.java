package com.pravoos.gateway.config;

import com.pravoos.gateway.security.TrustedProxyClientIpResolver;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver ipKeyResolver(TrustedProxyClientIpResolver clientIpResolver) {
        return exchange -> Mono.just(clientIpResolver.resolve(exchange.getRequest()));
    }
}
