package com.pravoos.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class TrustedProxyClientIpResolverTest {

  private static final String TRUSTED_PROXIES =
      "127.0.0.1/32,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16";

  private final TrustedProxyClientIpResolver resolver =
      new TrustedProxyClientIpResolver(TRUSTED_PROXIES);

  @Test
  void returnsUnknown_whenRemoteAddressAbsent() {
    ServerHttpRequest request = MockServerHttpRequest.get("/api/cases").build();

    assertThat(resolver.resolve(request)).isEqualTo("unknown");
  }

  @Test
  void skipsUntrustedHopInTheMiddleOfTheChain_returningItAsClient() {
    ServerHttpRequest request =
        MockServerHttpRequest.get("/api/cases")
            .remoteAddress(new InetSocketAddress("10.0.0.5", 443))
            .header("X-Forwarded-For", "203.0.113.9, 198.51.100.7, 10.0.0.2")
            .build();

    String client = resolver.resolve(request);

    assertThat(client).isEqualTo("198.51.100.7");
  }

  @Test
  void allHopsTrusted_fallsBackToRemoteAddress() {
    ServerHttpRequest request =
        MockServerHttpRequest.get("/api/cases")
            .remoteAddress(new InetSocketAddress("10.0.0.5", 443))
            .header("X-Forwarded-For", "10.0.0.2, 172.16.5.5")
            .build();

    assertThat(resolver.resolve(request)).isEqualTo("10.0.0.5");
  }

  @Test
  void trustsIpv6LoopbackProxy() {
    ServerHttpRequest request =
        MockServerHttpRequest.get("/api/cases")
            .remoteAddress(new InetSocketAddress("::1", 443))
            .header("X-Forwarded-For", "2001:db8::1")
            .build();

    assertThat(resolver.resolve(request)).isEqualTo("2001:db8::1");
  }
}
