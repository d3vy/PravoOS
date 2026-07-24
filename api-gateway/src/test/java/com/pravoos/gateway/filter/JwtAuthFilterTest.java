package com.pravoos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pravoos.common.security.JwtVerifier;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class JwtAuthFilterTest {

  private static final String TOKEN = "Bearer signed.jwt.token";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final long ISSUED_AT = 1_000_000L;

  private JwtVerifier jwtVerifier;
  private ReactiveStringRedisTemplate redisTemplate;
  private ReactiveValueOperations<String, String> valueOperations;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    jwtVerifier = mock(JwtVerifier.class);
    redisTemplate = mock(ReactiveStringRedisTemplate.class);
    valueOperations = mock(ReactiveValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID);
    when(claims.get("role", String.class)).thenReturn("LAWYER");
    when(claims.get("email", String.class)).thenReturn("lawyer@example.com");
    when(claims.getIssuedAt()).thenReturn(Date.from(Instant.ofEpochSecond(ISSUED_AT)));
    when(jwtVerifier.extractClaims(anyString())).thenReturn(claims);
  }

  @Test
  void validTokenWithEmptyDenylist_passesAndSetsVerifiedUserHeaders() {
    when(valueOperations.get(anyString())).thenReturn(Mono.empty());
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

    ServerWebExchange exchange = exchangeWithSpoofedHeaders();
    runFilter(true, exchange, forwarded);

    HttpHeaders headers = forwarded.get().getHeaders();
    assertThat(headers.getFirst("X-User-Id")).isEqualTo(USER_ID);
    assertThat(headers.getFirst("X-User-Role")).isEqualTo("LAWYER");
    assertThat(headers.getFirst("X-User-Email")).isEqualTo("lawyer@example.com");
  }

  @Test
  void tokenIssuedBeforeRevocation_isRejected() {
    when(valueOperations.get(anyString())).thenReturn(Mono.just(String.valueOf(ISSUED_AT + 1)));
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

    ServerWebExchange exchange = exchangeWithSpoofedHeaders();
    runFilter(true, exchange, forwarded);

    assertThat(forwarded.get()).isNull();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tokenIssuedAfterRevocation_passes() {
    when(valueOperations.get(anyString())).thenReturn(Mono.just(String.valueOf(ISSUED_AT - 1)));
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

    ServerWebExchange exchange = exchangeWithSpoofedHeaders();
    runFilter(true, exchange, forwarded);

    assertThat(forwarded.get()).isNotNull();
  }

  @Test
  void redisFailure_failOpen_passes() {
    when(valueOperations.get(anyString()))
        .thenReturn(Mono.error(new RuntimeException("redis down")));
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

    ServerWebExchange exchange = exchangeWithSpoofedHeaders();
    runFilter(true, exchange, forwarded);

    assertThat(forwarded.get()).isNotNull();
  }

  @Test
  void redisFailure_failClosed_isRejected() {
    when(valueOperations.get(anyString()))
        .thenReturn(Mono.error(new RuntimeException("redis down")));
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

    ServerWebExchange exchange = exchangeWithSpoofedHeaders();
    runFilter(false, exchange, forwarded);

    assertThat(forwarded.get()).isNull();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void missingBearerToken_isRejected() {
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
    ServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/cases"));

    runFilter(true, exchange, forwarded);

    assertThat(forwarded.get()).isNull();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void wrongRequiredRole_isForbidden() {
    when(valueOperations.get(anyString())).thenReturn(Mono.empty());
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
    ServerWebExchange exchange = exchangeWithSpoofedHeaders();

    JwtAuthFilter.Config config = new JwtAuthFilter.Config();
    config.setRequiredRole("ADMIN");
    GatewayFilter filter = new JwtAuthFilter(jwtVerifier, redisTemplate, true).apply(config);
    filter.filter(exchange, capturingChain(forwarded)).block();

    assertThat(forwarded.get()).isNull();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  private void runFilter(
      boolean failOpen, ServerWebExchange exchange, AtomicReference<ServerHttpRequest> forwarded) {
    GatewayFilter filter =
        new JwtAuthFilter(jwtVerifier, redisTemplate, failOpen).apply(new JwtAuthFilter.Config());
    filter.filter(exchange, capturingChain(forwarded)).block();
  }

  private GatewayFilterChain capturingChain(AtomicReference<ServerHttpRequest> forwarded) {
    return mutated -> {
      forwarded.set(mutated.getRequest());
      return Mono.empty();
    };
  }

  private ServerWebExchange exchangeWithSpoofedHeaders() {
    return MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/cases")
            .header(HttpHeaders.AUTHORIZATION, TOKEN)
            .header("X-User-Id", "spoofed-admin")
            .header("X-User-Role", "ADMIN"));
  }
}
