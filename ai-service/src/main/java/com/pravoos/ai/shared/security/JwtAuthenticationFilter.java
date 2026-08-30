package com.pravoos.ai.shared.security;

import com.pravoos.common.security.JwtVerifier;
import com.pravoos.common.web.AiProcessingMode;
import com.pravoos.common.web.OrgContext;
import com.pravoos.common.web.PlanLimits;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  private final JwtVerifier jwtVerifier;
  private final AccessTokenDenylist accessTokenDenylist;

  public JwtAuthenticationFilter(JwtVerifier jwtVerifier, AccessTokenDenylist accessTokenDenylist) {
    this.jwtVerifier = jwtVerifier;
    this.accessTokenDenylist = accessTokenDenylist;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = extractBearerToken(request);

    if (token != null && jwtVerifier.isValid(token)) {
      Claims claims = jwtVerifier.extractClaims(token);
      String role = claims.get("role", String.class);

      if (role != null && !role.isBlank() && !isRevoked(claims)) {
        List<SimpleGrantedAuthority> authorities =
            List.of(new SimpleGrantedAuthority("ROLE_" + role));
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
        authentication.setDetails(
            new OrgContext(
                extractUuidList(claims, "orgs"),
                extractUuidList(claims, "clients"),
                extractPlanLimits(claims),
                extractAiProcessingMode(claims)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    }

    chain.doFilter(request, response);
  }

  private List<UUID> extractUuidList(Claims claims, String claimName) {
    Object raw = claims.get(claimName);
    if (!(raw instanceof List<?> values)) {
      return List.of();
    }
    List<UUID> ids = new java.util.ArrayList<>(values.size());
    for (Object value : values) {
      try {
        ids.add(UUID.fromString(String.valueOf(value)));
      } catch (IllegalArgumentException ex) {
        log.warn("Dropping malformed entry in JWT claim '{}'", claimName);
      }
    }
    return Collections.unmodifiableList(ids);
  }

  private PlanLimits extractPlanLimits(Claims claims) {
    Object raw = claims.get("plan");
    if (!(raw instanceof Map<?, ?> plan)) {
      return null;
    }
    Object code = plan.get("code");
    if (code == null) {
      return null;
    }
    return new PlanLimits(
        String.valueOf(code),
        toNumber(plan.get("dailyRequests")).intValue(),
        toNumber(plan.get("dailyTokens")).longValue());
  }

  private AiProcessingMode extractAiProcessingMode(Claims claims) {
    Object raw = claims.get("aiMode");
    if (raw != null) {
      return AiProcessingMode.fromClaim(String.valueOf(raw));
    }
    return Boolean.TRUE.equals(claims.get("xb", Boolean.class))
        ? AiProcessingMode.CROSS_BORDER
        : AiProcessingMode.DEFAULT;
  }

  private Number toNumber(Object value) {
    return value instanceof Number number ? number : 0L;
  }

  private boolean isRevoked(Claims claims) {
    Date issuedAt = claims.getIssuedAt();
    long issuedAtSeconds = issuedAt != null ? issuedAt.toInstant().getEpochSecond() : 0L;
    return accessTokenDenylist.isRevoked(claims.getSubject(), issuedAtSeconds)
        || accessTokenDenylist.isSessionRevoked(claims.get("sid", String.class));
  }

  private String extractBearerToken(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    return null;
  }
}
