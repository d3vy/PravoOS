package com.pravoos.user.identity.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.common.web.AiProcessingMode;
import com.pravoos.user.billing.api.PlanClaim;
import com.pravoos.user.identity.internal.config.JwtProperties;
import com.pravoos.user.identity.model.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private static final long ACCESS_EXPIRATION_MS = 900_000L;
  private static final KeyPair KEY_PAIR = generateKeyPair();

  private final JwtTokenProvider provider =
      new JwtTokenProvider(
          new JwtProperties(privateKeyPem(), publicKeyPem(), ACCESS_EXPIRATION_MS, 0L));

  @Test
  void generatedTokenContainsSubjectEmailAndRole() {
    UUID userId = UUID.randomUUID();

    String token =
        provider.generateToken(
            userId,
            "user@pravoos.com",
            UserRole.LAWYER,
            null,
            null,
            null,
            AiProcessingMode.RU_ONLY,
            null);
    Claims claims = parse(token);

    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.get("email", String.class)).isEqualTo("user@pravoos.com");
    assertThat(claims.get("role", String.class)).isEqualTo("LAWYER");
  }

  @Test
  void generatedTokenHasIssuedAtAndExpirationMatchingConfiguredTtl() {
    UUID userId = UUID.randomUUID();

    long before = System.currentTimeMillis();
    String token =
        provider.generateToken(
            userId,
            "user@pravoos.com",
            UserRole.ADMIN,
            null,
            null,
            null,
            AiProcessingMode.RU_ONLY,
            null);
    long after = System.currentTimeMillis();
    Claims claims = parse(token);

    long secondPrecisionMs = 1_000L;
    assertThat(claims.getIssuedAt().getTime()).isBetween(before - secondPrecisionMs, after);
    assertThat(claims.getExpiration().getTime())
        .isBetween(before + ACCESS_EXPIRATION_MS - secondPrecisionMs, after + ACCESS_EXPIRATION_MS);
  }

  @Test
  void orgsAndClientsClaimsAreOmittedWhenNullOrEmpty() {
    UUID userId = UUID.randomUUID();

    String token =
        provider.generateToken(
            userId,
            "user@pravoos.com",
            UserRole.CLIENT,
            List.of(),
            null,
            null,
            AiProcessingMode.RU_ONLY,
            null);
    Claims claims = parse(token);

    assertThat(claims.get("orgs")).isNull();
    assertThat(claims.get("clients")).isNull();
  }

  @Test
  void orgsAndClientsClaimsAreIncludedWhenPresent() {
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();

    String token =
        provider.generateToken(
            userId,
            "user@pravoos.com",
            UserRole.LAWYER,
            List.of(orgId),
            List.of(clientId),
            null,
            AiProcessingMode.RU_ONLY,
            null);
    Claims claims = parse(token);

    List<?> orgs = claims.get("orgs", List.class);
    List<?> clients = claims.get("clients", List.class);

    assertThat(orgs).isEqualTo(List.of(orgId.toString()));
    assertThat(clients).isEqualTo(List.of(clientId.toString()));
  }

  @Test
  void planClaimIsSerializedAsMapWhenPresent() {
    UUID userId = UUID.randomUUID();
    PlanClaim plan = new PlanClaim("PRO", 100, 50_000L);

    String token =
        provider.generateToken(
            userId,
            "user@pravoos.com",
            UserRole.LAWYER,
            null,
            null,
            plan,
            AiProcessingMode.RU_ONLY,
            null);
    Claims claims = parse(token);

    Map<String, Object> planClaim = claims.get("plan", Map.class);
    assertThat(planClaim).containsEntry("code", "PRO").containsEntry("dailyRequests", 100);
  }

  @Test
  void planClaimIsOmittedWhenNull() {
    UUID userId = UUID.randomUUID();

    String token =
        provider.generateToken(
            userId,
            "user@pravoos.com",
            UserRole.LAWYER,
            null,
            null,
            null,
            AiProcessingMode.RU_ONLY,
            null);
    Claims claims = parse(token);

    assertThat(claims.get("plan")).isNull();
  }

  @Test
  void ruOnlyModeOmitsCrossBorderClaim() {
    UUID userId = UUID.randomUUID();

    String token =
        provider.generateToken(
            userId,
            "user@pravoos.com",
            UserRole.LAWYER,
            null,
            null,
            null,
            AiProcessingMode.RU_ONLY,
            null);
    Claims claims = parse(token);

    assertThat(claims.get("aiMode", String.class)).isEqualTo("RU_ONLY");
    assertThat(claims.get("xb")).isNull();
  }

  @Test
  void disabledModeOmitsCrossBorderClaim() {
    UUID userId = UUID.randomUUID();

    String token =
        provider.generateToken(
            userId,
            "user@pravoos.com",
            UserRole.LAWYER,
            null,
            null,
            null,
            AiProcessingMode.DISABLED,
            null);
    Claims claims = parse(token);

    assertThat(claims.get("aiMode", String.class)).isEqualTo("DISABLED");
    assertThat(claims.get("xb")).isNull();
  }

  @Test
  void crossBorderModeSetsBothClaims() {
    UUID userId = UUID.randomUUID();

    String token =
        provider.generateToken(
            userId,
            "user@pravoos.com",
            UserRole.LAWYER,
            null,
            null,
            null,
            AiProcessingMode.CROSS_BORDER,
            null);
    Claims claims = parse(token);

    assertThat(claims.get("aiMode", String.class)).isEqualTo("CROSS_BORDER");
    assertThat(claims.get("xb", Boolean.class)).isTrue();
  }

  @Test
  void nullModeFallsBackToDefault() {
    UUID userId = UUID.randomUUID();

    String token =
        provider.generateToken(
            userId, "user@pravoos.com", UserRole.LAWYER, null, null, null, null, null);
    Claims claims = parse(token);

    assertThat(claims.get("aiMode", String.class)).isEqualTo(AiProcessingMode.DEFAULT.name());
  }

  @Test
  void sessionIdIsCarriedAsSidClaimAndOmittedWhenAbsent() {
    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();

    Claims withSession =
        parse(
            provider.generateToken(
                userId, "user@pravoos.com", UserRole.LAWYER, null, null, null, null, sessionId));
    Claims withoutSession =
        parse(
            provider.generateToken(
                userId, "user@pravoos.com", UserRole.LAWYER, null, null, null, null, null));

    assertThat(withSession.get("sid", String.class)).isEqualTo(sessionId.toString());
    assertThat(withoutSession.get("sid", String.class)).isNull();
  }

  private Claims parse(String token) {
    return Jwts.parser()
        .verifyWith((RSAPublicKey) KEY_PAIR.getPublic())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  private static String privateKeyPem() {
    String base64 =
        Base64.getEncoder().encodeToString(((RSAPrivateKey) KEY_PAIR.getPrivate()).getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
  }

  private static String publicKeyPem() {
    String base64 =
        Base64.getEncoder().encodeToString(((RSAPublicKey) KEY_PAIR.getPublic()).getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
  }

  private static KeyPair generateKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
