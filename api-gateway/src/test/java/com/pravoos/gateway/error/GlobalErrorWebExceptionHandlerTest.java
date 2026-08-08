package com.pravoos.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

class GlobalErrorWebExceptionHandlerTest {

  private final GlobalErrorWebExceptionHandler handler = new GlobalErrorWebExceptionHandler();

  @Test
  void downstreamUnavailable_mapsResponseStatusExceptionToItsStatus() {
    ServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/ai/chat"));
    ResponseStatusException ex =
        new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "downstream unreachable");

    handler.handle(exchange, ex).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(bodyAsString(exchange)).isEqualTo("{\"error\":\"Service Unavailable\"}");
  }

  @Test
  void unexpectedException_mapsTo500() {
    ServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/ai/chat"));

    handler.handle(exchange, new IllegalStateException("boom")).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(bodyAsString(exchange)).isEqualTo("{\"error\":\"Internal Server Error\"}");
  }

  @Test
  void setsJsonContentType() {
    ServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/ai/chat"));

    handler.handle(exchange, new IllegalStateException("boom")).block();

    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
  }

  @Test
  void committedResponse_propagatesOriginalError() {
    ServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/ai/chat"));
    exchange.getResponse().setComplete().block();
    RuntimeException original = new RuntimeException("already streaming");

    assertThatThrownBy(() -> handler.handle(exchange, original).block()).isSameAs(original);
  }

  private String bodyAsString(ServerWebExchange exchange) {
    var buffer =
        ((org.springframework.mock.http.server.reactive.MockServerHttpResponse)
                exchange.getResponse())
            .getBodyAsString()
            .block();
    return buffer;
  }
}
