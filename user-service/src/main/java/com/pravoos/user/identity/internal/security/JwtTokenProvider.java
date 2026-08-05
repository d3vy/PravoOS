package com.pravoos.user.identity.internal.security;

import com.pravoos.common.security.RsaKeyLoader;
import com.pravoos.common.web.AiProcessingMode;
import com.pravoos.user.billing.api.PlanClaim;
import com.pravoos.user.identity.internal.config.JwtProperties;
import com.pravoos.user.identity.model.enums.UserRole;
import io.jsonwebtoken.Jwts;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private final RSAPrivateKey privateKey;
  private final long accessExpirationMs;

  public JwtTokenProvider(JwtProperties jwtProperties) {
    this.privateKey = RsaKeyLoader.loadPrivateKey(jwtProperties.privateKey());
    this.accessExpirationMs = jwtProperties.accessExpirationMs();
  }

  public String generateToken(
      UUID userId,
      String email,
      UserRole role,
      List<UUID> orgIds,
      List<UUID> clientIds,
      PlanClaim plan,
      AiProcessingMode aiProcessingMode) {
    var builder =
        Jwts.builder().subject(userId.toString()).claim("email", email).claim("role", role.name());
    if (orgIds != null && !orgIds.isEmpty()) {
      builder.claim("orgs", orgIds.stream().map(UUID::toString).toList());
    }
    if (clientIds != null && !clientIds.isEmpty()) {
      builder.claim("clients", clientIds.stream().map(UUID::toString).toList());
    }
    if (plan != null) {
      builder.claim(
          "plan",
          Map.of(
              "code", plan.code(),
              "dailyRequests", plan.dailyRequests(),
              "dailyTokens", plan.dailyTokens()));
    }
    AiProcessingMode mode = aiProcessingMode == null ? AiProcessingMode.DEFAULT : aiProcessingMode;
    builder.claim("aiMode", mode.name());
    if (mode.allowsCrossBorderTransfer()) {
      builder.claim("xb", true);
    }
    return builder
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + accessExpirationMs))
        .signWith(privateKey)
        .compact();
  }
}
