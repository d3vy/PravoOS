package com.pravoos.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.gateway.security.TrustedProxyClientIpResolver;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimiterConfigTest {

  private static final String TRUSTED_PROXIES =
      "127.0.0.1/32,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16";

  private final KeyResolver resolver =
      new RateLimiterConfig().ipKeyResolver(new TrustedProxyClientIpResolver(TRUSTED_PROXIES));

  @Test
  void returnsRealClientAppendedByTrustedProxy_ignoringSpoofedLeftmost() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/auth/login")
            .remoteAddress(new InetSocketAddress("10.0.0.5", 443))
            .header("X-Forwarded-For", "9.9.9.9, 203.0.113.7")
            .build();

    String key = resolver.resolve(MockServerWebExchange.from(request)).block();

    assertThat(key).isEqualTo("203.0.113.7");
  }

  @Test
  void ignoresForwardedHeader_whenImmediatePeerIsUntrusted() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/auth/login")
            .remoteAddress(new InetSocketAddress("198.51.100.5", 12345))
            .header("X-Forwarded-For", "9.9.9.9")
            .build();

    String key = resolver.resolve(MockServerWebExchange.from(request)).block();

    assertThat(key).isEqualTo("198.51.100.5");
  }

  @Test
  void fallsBackToRemoteAddress_whenNoForwardedHeader() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/auth/login")
            .remoteAddress(new InetSocketAddress("10.0.0.5", 443))
            .build();

    String key = resolver.resolve(MockServerWebExchange.from(request)).block();

    assertThat(key).isEqualTo("10.0.0.5");
  }
}
