package com.pravoos.gateway.filter;

import com.pravoos.gateway.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Date;

@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DENYLIST_KEY_PREFIX = "auth:revoked_after:";

    private final JwtTokenProvider jwtTokenProvider;
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider, ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                return unauthorized(exchange);
            }

            String token = authHeader.substring(BEARER_PREFIX.length());

            Claims claims;
            try {
                claims = jwtTokenProvider.extractClaims(token);
            } catch (JwtException | IllegalArgumentException e) {
                return unauthorized(exchange);
            }

            String role = claims.get("role", String.class);
            if (config.getRequiredRole() != null && !config.getRequiredRole().equals(role)) {
                return forbidden(exchange);
            }

            String subject = claims.getSubject();
            long issuedAtSeconds = issuedAtSeconds(claims);

            return isAccessTokenRevoked(subject, issuedAtSeconds).flatMap(revoked -> {
                if (revoked) {
                    return unauthorized(exchange);
                }
                ServerWebExchange mutatedExchange = exchange.mutate()
                        .request(r -> r.headers(headers -> {
                            headers.remove("X-User-Id");
                            headers.remove("X-User-Role");
                            headers.remove("X-User-Email");
                            putIfPresent(headers, "X-User-Id", subject);
                            putIfPresent(headers, "X-User-Role", role);
                            putIfPresent(headers, "X-User-Email", claims.get("email", String.class));
                        }))
                        .build();
                return chain.filter(mutatedExchange);
            });
        };
    }

    private Mono<Boolean> isAccessTokenRevoked(String userId, long issuedAtSeconds) {
        if (userId == null) {
            return Mono.just(false);
        }
        return redisTemplate.opsForValue().get(DENYLIST_KEY_PREFIX + userId)
                .map(value -> {
                    try {
                        return issuedAtSeconds < Long.parseLong(value.trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                })
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    private long issuedAtSeconds(Claims claims) {
        Date issuedAt = claims.getIssuedAt();
        return issuedAt != null ? issuedAt.toInstant().getEpochSecond() : 0L;
    }

    private void putIfPresent(HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    public static class Config {

        private String requiredRole;

        public String getRequiredRole() {
            return requiredRole;
        }

        public void setRequiredRole(String requiredRole) {
            this.requiredRole = requiredRole;
        }
    }
}
