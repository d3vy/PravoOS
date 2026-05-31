package com.pravoos.gateway.filter;

import com.pravoos.gateway.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<Object> {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
        super(Object.class);
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public GatewayFilter apply(Object config) {
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

            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(r -> r.headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Role");
                        headers.remove("X-User-Email");
                        putIfPresent(headers, "X-User-Id", claims.getSubject());
                        putIfPresent(headers, "X-User-Role", claims.get("role", String.class));
                        putIfPresent(headers, "X-User-Email", claims.get("email", String.class));
                    }))
                    .build();

            return chain.filter(mutatedExchange);
        };
    }

    private void putIfPresent(org.springframework.http.HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
