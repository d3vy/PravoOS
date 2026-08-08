package com.pravoos.gateway.config;

import com.pravoos.gateway.filter.JwtAuthFilter;
import com.pravoos.gateway.security.TrustedProxyClientIpResolver;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

  private static final String UNROUTED = "unrouted";

  @Bean
  public KeyResolver ipKeyResolver(TrustedProxyClientIpResolver clientIpResolver) {
    return exchange ->
        Mono.just(scoped(exchange, "ip:" + clientIpResolver.resolve(exchange.getRequest())));
  }

  @Bean
  public KeyResolver userKeyResolver(TrustedProxyClientIpResolver clientIpResolver) {
    return exchange -> {
      Object userId = exchange.getAttribute(JwtAuthFilter.AUTHENTICATED_USER_ATTRIBUTE);
      String principal =
          userId instanceof String id && !id.isBlank()
              ? "user:" + id
              : "ip:" + clientIpResolver.resolve(exchange.getRequest());
      return Mono.just(scoped(exchange, principal));
    };
  }

  private String scoped(ServerWebExchange exchange, String principal) {
    return routeId(exchange) + "|" + principal;
  }

  private String routeId(ServerWebExchange exchange) {
    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    return route == null ? UNROUTED : route.getId();
  }
}
