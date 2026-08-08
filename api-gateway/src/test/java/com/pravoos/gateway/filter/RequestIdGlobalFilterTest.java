package com.pravoos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

class RequestIdGlobalFilterTest {

  private final RequestIdGlobalFilter filter = new RequestIdGlobalFilter();

  @Test
  void generatesRequestId_whenHeaderMissing() {
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
    ServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/cases"));

    filter.filter(exchange, capturingChain(forwarded)).block();

    String forwardedId = forwarded.get().getHeaders().getFirst("X-Request-Id");
    assertThat(forwardedId).isNotBlank();
    assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isEqualTo(forwardedId);
  }

  @Test
  void generatesRequestId_whenHeaderBlank() {
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
    ServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/cases").header("X-Request-Id", "  "));

    filter.filter(exchange, capturingChain(forwarded)).block();

    assertThat(forwarded.get().getHeaders().getFirst("X-Request-Id")).isNotBlank();
  }

  @Test
  void reusesExistingRequestId() {
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
    ServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/cases").header("X-Request-Id", "existing-id"));

    filter.filter(exchange, capturingChain(forwarded)).block();

    assertThat(forwarded.get().getHeaders().getFirst("X-Request-Id")).isEqualTo("existing-id");
    assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id"))
        .isEqualTo("existing-id");
  }

  @Test
  void runsAtHighestPrecedence() {
    assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }

  private org.springframework.cloud.gateway.filter.GatewayFilterChain capturingChain(
      AtomicReference<ServerHttpRequest> forwarded) {
    return mutated -> {
      forwarded.set(mutated.getRequest());
      return reactor.core.publisher.Mono.empty();
    };
  }
}
