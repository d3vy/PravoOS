package com.pravoos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pravoos.gateway.security.TrustedProxyClientIpResolver;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class UserHeaderSanitizingFilterTest {

  private final TrustedProxyClientIpResolver resolver = mock(TrustedProxyClientIpResolver.class);
  private final UserHeaderSanitizingFilter filter = new UserHeaderSanitizingFilter(resolver);

  @Test
  void stripsSpoofedUserHeaders_andSetsResolvedClientIp() {
    when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("203.0.113.7");
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
    ServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/cases")
                .header("X-User-Id", "spoofed-admin")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Email", "spoofed@example.com")
                .header("X-Client-Ip", "9.9.9.9"));

    filter.filter(exchange, capturingChain(forwarded)).block();

    var headers = forwarded.get().getHeaders();
    assertThat(headers.getFirst("X-User-Id")).isNull();
    assertThat(headers.getFirst("X-User-Role")).isNull();
    assertThat(headers.getFirst("X-User-Email")).isNull();
    assertThat(headers.getFirst("X-Client-Ip")).isEqualTo("203.0.113.7");
  }

  @Test
  void runsAtHighestPrecedence() {
    assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }

  private org.springframework.cloud.gateway.filter.GatewayFilterChain capturingChain(
      AtomicReference<ServerHttpRequest> forwarded) {
    return mutated -> {
      forwarded.set(mutated.getRequest());
      return Mono.empty();
    };
  }
}
