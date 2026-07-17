package com.pravoos.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String existingId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        String requestId = (existingId == null || existingId.isBlank())
                ? UUID.randomUUID().toString()
                : existingId;

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(r -> r.headers(headers -> headers.set(REQUEST_ID_HEADER, requestId)))
                .build();
        mutatedExchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}
