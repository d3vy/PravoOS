package com.pravoos.gateway.filter;

import com.pravoos.gateway.security.TrustedProxyClientIpResolver;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class UserHeaderSanitizingFilter implements GlobalFilter, Ordered {

    private static final String CLIENT_IP_HEADER = "X-Client-Ip";

    private final TrustedProxyClientIpResolver clientIpResolver;

    public UserHeaderSanitizingFilter(TrustedProxyClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = clientIpResolver.resolve(exchange.getRequest());
        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(r -> r.headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Role");
                    headers.remove("X-User-Email");
                    headers.remove(CLIENT_IP_HEADER);
                    headers.set(CLIENT_IP_HEADER, clientIp);
                }))
                .build();
        return chain.filter(sanitizedExchange);
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}
