package com.pravoos.ai.shared.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.shared.config.JwtProperties;
import com.pravoos.ai.shared.config.SecurityConfig;
import com.pravoos.common.web.SecurityUtils;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Exercises the real authentication mechanism end to end — a signed token travelling through {@link
 * JwtAuthenticationFilter} into the filter chain — rather than the pre-populated security context
 * that {@code @WithMockUser} installs. Token rejection, role mapping and the revocation denylist
 * are the parts a framework upgrade can break without any compilation error.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(
    classes = {JwtAuthenticationChainTest.TestWebConfig.class, SecurityConfig.class})
class JwtAuthenticationChainTest {

  private static final String PROBE_PATH = "/api/ai/cases/1";
  private static final UUID USER_ID = UUID.randomUUID();

  private static KeyPair keyPair;

  @Autowired private AccessTokenDenylist accessTokenDenylist;

  private MockMvc mockMvc;

  @BeforeAll
  static void generateKeyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keyPair = generator.generateKeyPair();
  }

  @BeforeEach
  void setUp(WebApplicationContext webApplicationContext) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    Mockito.reset(accessTokenDenylist);
    Mockito.when(accessTokenDenylist.isRevoked(anyString(), anyLong())).thenReturn(false);
    Mockito.when(accessTokenDenylist.isSessionRevoked(any())).thenReturn(false);
  }

  @Test
  void acceptsValidTokenAndExposesSubjectAsPrincipal() throws Exception {
    mockMvc
        .perform(authorized(signedToken(keyPair.getPrivate(), "LAWYER", Instant.now())))
        .andExpect(status().isOk())
        .andExpect(content().string(USER_ID.toString()));
  }

  @Test
  void rejectsRequestWithoutToken() throws Exception {
    mockMvc.perform(get(PROBE_PATH)).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsMalformedToken() throws Exception {
    mockMvc.perform(authorized("not-a-jwt")).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsTokenSignedByAnotherKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    String foreignToken =
        signedToken(generator.generateKeyPair().getPrivate(), "LAWYER", Instant.now());

    mockMvc.perform(authorized(foreignToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsExpiredToken() throws Exception {
    String expired =
        Jwts.builder()
            .subject(USER_ID.toString())
            .claim("role", "LAWYER")
            .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
            .expiration(Date.from(Instant.now().minusSeconds(3600)))
            .signWith(keyPair.getPrivate())
            .compact();

    mockMvc.perform(authorized(expired)).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsTokenWithoutRoleClaim() throws Exception {
    String roleless =
        Jwts.builder()
            .subject(USER_ID.toString())
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(keyPair.getPrivate())
            .compact();

    mockMvc.perform(authorized(roleless)).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsTokenIssuedBeforeUserRevocationCutoff() throws Exception {
    Mockito.when(accessTokenDenylist.isRevoked(anyString(), anyLong())).thenReturn(true);

    mockMvc
        .perform(authorized(signedToken(keyPair.getPrivate(), "LAWYER", Instant.now())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsTokenOfRevokedSession() throws Exception {
    Mockito.when(accessTokenDenylist.isSessionRevoked(any())).thenReturn(true);

    mockMvc
        .perform(authorized(signedToken(keyPair.getPrivate(), "LAWYER", Instant.now())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void mapsRoleClaimToAuthorityUsedByRouteRules() throws Exception {
    mockMvc
        .perform(authorized(signedToken(keyPair.getPrivate(), "CLIENT", Instant.now())))
        .andExpect(status().isForbidden());
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      authorized(String token) {
    return get(PROBE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private static String signedToken(PrivateKey signingKey, String role, Instant issuedAt) {
    return Jwts.builder()
        .subject(USER_ID.toString())
        .claim("role", role)
        .claim("sid", UUID.randomUUID().toString())
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(issuedAt.plusSeconds(3600)))
        .signWith(signingKey)
        .compact();
  }

  @Configuration
  @EnableWebMvc
  @Import(JwtAuthenticationChainTest.PrincipalProbeController.class)
  static class TestWebConfig {

    @Bean
    JwtProperties jwtProperties() {
      return new JwtProperties(
          Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
    }

    @Bean
    AccessTokenDenylist accessTokenDenylist() {
      return Mockito.mock(AccessTokenDenylist.class);
    }
  }

  @RestController
  static class PrincipalProbeController {

    @GetMapping(PROBE_PATH)
    String currentUser(Authentication authentication) {
      return SecurityUtils.currentUserId(authentication).toString();
    }
  }
}
