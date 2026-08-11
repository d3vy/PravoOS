package com.pravoos.gateway.filter;

import com.pravoos.common.security.JwtVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String DENYLIST_KEY_PREFIX = "auth:revoked_after:";
  private static final String SESSION_DENYLIST_KEY_PREFIX = "auth:revoked_sid:";

  public static final String AUTHENTICATED_USER_ATTRIBUTE = "pravoos.gateway.authenticatedUserId";

  private final JwtVerifier jwtVerifier;
  private final ReactiveStringRedisTemplate redisTemplate;
  private final boolean denylistFailOpen;

  public JwtAuthFilter(
      JwtVerifier jwtVerifier,
      ReactiveStringRedisTemplate redisTemplate,
      @Value("${app.gateway.denylist.fail-open:true}") boolean denylistFailOpen) {
    super(Config.class);
    this.jwtVerifier = jwtVerifier;
    this.redisTemplate = redisTemplate;
    this.denylistFailOpen = denylistFailOpen;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
      if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
        return unauthorized(exchange);
      }

      String token = authHeader.substring(BEARER_PREFIX.length());

      Claims claims;
      try {
        claims = jwtVerifier.extractClaims(token);
      } catch (JwtException | IllegalArgumentException e) {
        return unauthorized(exchange);
      }

      String role = claims.get("role", String.class);
      if (config.getRequiredRole() != null && !config.getRequiredRole().equals(role)) {
        return forbidden(exchange);
      }

      String subject = claims.getSubject();
      long issuedAtSeconds = issuedAtSeconds(claims);
      String sessionId = claims.get("sid", String.class);

      return isRevoked(subject, issuedAtSeconds, sessionId)
          .flatMap(
              revoked -> {
                if (revoked) {
                  return unauthorized(exchange);
                }
                if (subject != null && !subject.isBlank()) {
                  exchange.getAttributes().put(AUTHENTICATED_USER_ATTRIBUTE, subject);
                }
                ServerWebExchange mutatedExchange =
                    exchange
                        .mutate()
                        .request(
                            r ->
                                r.headers(
                                    headers -> {
                                      headers.remove("X-User-Id");
                                      headers.remove("X-User-Role");
                                      headers.remove("X-User-Email");
                                      putIfPresent(headers, "X-User-Id", subject);
                                      putIfPresent(headers, "X-User-Role", role);
                                    }))
                        .build();
                return chain.filter(mutatedExchange);
              });
    };
  }

  private Mono<Boolean> isRevoked(String userId, long issuedAtSeconds, String sessionId) {
    return isAccessTokenRevoked(userId, issuedAtSeconds)
        .flatMap(revoked -> revoked ? Mono.just(true) : isSessionRevoked(sessionId));
  }

  private Mono<Boolean> isSessionRevoked(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return Mono.just(false);
    }
    return redisTemplate
        .hasKey(SESSION_DENYLIST_KEY_PREFIX + sessionId)
        .defaultIfEmpty(false)
        .onErrorResume(
            ex -> {
              log.warn(
                  "Redis session-denylist check failed for session {}, fail-open={}",
                  sessionId,
                  denylistFailOpen,
                  ex);
              return Mono.just(!denylistFailOpen);
            });
  }

  private Mono<Boolean> isAccessTokenRevoked(String userId, long issuedAtSeconds) {
    if (userId == null) {
      return Mono.just(false);
    }
    return redisTemplate
        .opsForValue()
        .get(DENYLIST_KEY_PREFIX + userId)
        .map(
            value -> {
              try {
                return issuedAtSeconds <= Long.parseLong(value.trim());
              } catch (NumberFormatException e) {
                return false;
              }
            })
        .defaultIfEmpty(false)
        .onErrorResume(
            ex -> {
              log.warn(
                  "Redis denylist check failed for user {}, fail-open={}",
                  userId,
                  denylistFailOpen,
                  ex);
              return Mono.just(!denylistFailOpen);
            });
  }

  private long issuedAtSeconds(Claims claims) {
    Date issuedAt = claims.getIssuedAt();
    return issuedAt != null ? issuedAt.toInstant().getEpochSecond() : 0L;
  }

  private void putIfPresent(HttpHeaders headers, String name, String value) {
    if (value != null && !value.isBlank()) {
      headers.set(name, value);
    }
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();
  }

  private Mono<Void> forbidden(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
    return exchange.getResponse().setComplete();
  }

  public static class Config {

    private String requiredRole;

    public String getRequiredRole() {
      return requiredRole;
    }

    public void setRequiredRole(String requiredRole) {
      this.requiredRole = requiredRole;
    }
  }
}
