package com.pravoos.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.gateway.filter.JwtAuthFilter;
import com.pravoos.gateway.security.TrustedProxyClientIpResolver;
import java.net.InetSocketAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimiterConfigTest {

  private static final String TRUSTED_PROXIES =
      "127.0.0.1/32,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16";

  private final RateLimiterConfig config = new RateLimiterConfig();
  private final TrustedProxyClientIpResolver clientIpResolver =
      new TrustedProxyClientIpResolver(TRUSTED_PROXIES);
  private final KeyResolver ipResolver = config.ipKeyResolver(clientIpResolver);
  private final KeyResolver userResolver = config.userKeyResolver(clientIpResolver);

  @Test
  void returnsRealClientAppendedByTrustedProxy_ignoringSpoofedLeftmost() {
    MockServerWebExchange exchange =
        routed(
            "auth-route",
            MockServerHttpRequest.get("/api/auth/login")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 443))
                .header("X-Forwarded-For", "9.9.9.9, 203.0.113.7"));

    assertThat(ipResolver.resolve(exchange).block()).isEqualTo("auth-route|ip:203.0.113.7");
  }

  @Test
  void ignoresForwardedHeader_whenImmediatePeerIsUntrusted() {
    MockServerWebExchange exchange =
        routed(
            "auth-route",
            MockServerHttpRequest.get("/api/auth/login")
                .remoteAddress(new InetSocketAddress("198.51.100.5", 12345))
                .header("X-Forwarded-For", "9.9.9.9"));

    assertThat(ipResolver.resolve(exchange).block()).isEqualTo("auth-route|ip:198.51.100.5");
  }

  @Test
  void fallsBackToRemoteAddress_whenNoForwardedHeader() {
    MockServerWebExchange exchange =
        routed(
            "auth-route",
            MockServerHttpRequest.get("/api/auth/login")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 443)));

    assertThat(ipResolver.resolve(exchange).block()).isEqualTo("auth-route|ip:10.0.0.5");
  }

  @Test
  void sameClientGetsSeparateBucketPerRoute() {
    MockServerHttpRequest.BaseBuilder<?> request =
        MockServerHttpRequest.get("/api/ai/chat")
            .remoteAddress(new InetSocketAddress("10.0.0.5", 443));

    String onAi = ipResolver.resolve(routed("ai-service", request)).block();
    String onAuth = ipResolver.resolve(routed("user-service-auth", request)).block();

    assertThat(onAi).isNotEqualTo(onAuth);
  }

  @Test
  void authenticatedRequestsFromOneOfficeIpGetSeparateBucketsPerUser() {
    MockServerHttpRequest.BaseBuilder<?> request =
        MockServerHttpRequest.get("/api/ai/chat")
            .remoteAddress(new InetSocketAddress("10.0.0.5", 443))
            .header("X-Forwarded-For", "203.0.113.7");

    MockServerWebExchange first = routed("ai-service", request);
    first.getAttributes().put(JwtAuthFilter.AUTHENTICATED_USER_ATTRIBUTE, "lawyer-1");
    MockServerWebExchange second = routed("ai-service", request);
    second.getAttributes().put(JwtAuthFilter.AUTHENTICATED_USER_ATTRIBUTE, "lawyer-2");

    assertThat(userResolver.resolve(first).block()).isEqualTo("ai-service|user:lawyer-1");
    assertThat(userResolver.resolve(second).block()).isEqualTo("ai-service|user:lawyer-2");
  }

  @Test
  void userResolverFallsBackToIpWhenUnauthenticated() {
    MockServerWebExchange exchange =
        routed(
            "ai-service",
            MockServerHttpRequest.get("/api/ai/chat")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 443)));

    assertThat(userResolver.resolve(exchange).block()).isEqualTo("ai-service|ip:10.0.0.5");
  }

  private MockServerWebExchange routed(
      String routeId, MockServerHttpRequest.BaseBuilder<?> builder) {
    MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());
    exchange
        .getAttributes()
        .put(
            ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
            Route.async()
                .id(routeId)
                .uri(URI.create("http://localhost:8081"))
                .predicate(e -> true)
                .build());
    return exchange;
  }
}
