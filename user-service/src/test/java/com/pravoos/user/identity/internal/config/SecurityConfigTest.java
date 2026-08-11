package com.pravoos.user.identity.internal.config;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.common.security.internal.InternalCallerHeaders;
import com.pravoos.common.security.internal.InternalCallerProperties;
import com.pravoos.common.security.internal.InternalCallerProperties.Caller;
import com.pravoos.user.identity.api.TokenDenylistService;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Pins down every matcher in {@link SecurityConfig}, including the internal-caller chain: {@code
 * /internal/**} is permitAll at the authorization layer and is guarded solely by {@code
 * InternalSecretFilter}, so a regression there would expose service-to-service endpoints.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {SecurityConfigTest.TestWebConfig.class, SecurityConfig.class})
class SecurityConfigTest {

  private static final String LAWYER = "LAWYER";
  private static final String ADMIN = "ADMIN";
  private static final String CLIENT = "CLIENT";
  private static final List<String> ALL_ROLES = List.of(LAWYER, ADMIN, CLIENT);

  private static final String INTERNAL_CALLER = "ai-service";
  private static final String INTERNAL_SECRET = "internal-secret-value";
  private static final String ALLOWED_INTERNAL_PATH = "/internal/users/1";
  private static final String FORBIDDEN_INTERNAL_PATH = "/internal/secrets/1";

  private static final List<String> PUBLIC_PATHS =
      List.of(
          "/api/auth/login",
          "/actuator/health",
          "/actuator/health/readiness",
          "/actuator/prometheus",
          "/api/user/privacy/policy",
          "/v3/api-docs/swagger-config",
          "/swagger-ui/index.html",
          "/swagger-ui.html");

  private static final List<String> ADMIN_ONLY_PATHS = List.of("/api/admin/users");

  private static final List<String> LAWYER_AND_ADMIN_PATHS = List.of("/api/user/billing/plan");

  private static final List<String> AUTHENTICATED_PATHS = List.of("/api/user/profile");

  private MockMvc mockMvc;

  @BeforeEach
  void setUp(WebApplicationContext webApplicationContext) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @ParameterizedTest(name = "{1} on {0} -> {2}")
  @MethodSource("roleMatrix")
  void enforcesRolePerRoute(String path, String role, int expectedStatus) throws Exception {
    mockMvc
        .perform(get(path).with(user("probe").roles(role)))
        .andExpect(status().is(expectedStatus));
  }

  @ParameterizedTest(name = "anonymous on {0} -> 401")
  @MethodSource("protectedPaths")
  void rejectsAnonymousOnEveryProtectedRoute(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest(name = "anonymous on {0} -> 200")
  @MethodSource("publicPaths")
  void allowsAnonymousOnPublicRoutes(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isOk());
  }

  @Test
  void allowsAnonymousBillingWebhook() throws Exception {
    mockMvc.perform(post("/api/billing/webhook")).andExpect(status().isOk());
  }

  @Test
  void rejectsInternalCallWithoutCredentials() throws Exception {
    mockMvc.perform(get(ALLOWED_INTERNAL_PATH)).andExpect(status().isForbidden());
  }

  @Test
  void rejectsInternalCallWithWrongSecret() throws Exception {
    mockMvc
        .perform(
            get(ALLOWED_INTERNAL_PATH)
                .header(InternalCallerHeaders.CALLER, INTERNAL_CALLER)
                .header(InternalCallerHeaders.SECRET, "wrong-secret"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsInternalCallFromUnknownCaller() throws Exception {
    mockMvc
        .perform(
            get(ALLOWED_INTERNAL_PATH)
                .header(InternalCallerHeaders.CALLER, "attacker")
                .header(InternalCallerHeaders.SECRET, INTERNAL_SECRET))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsInternalCallToPathOutsideCallerAllowlist() throws Exception {
    mockMvc
        .perform(
            get(FORBIDDEN_INTERNAL_PATH)
                .header(InternalCallerHeaders.CALLER, INTERNAL_CALLER)
                .header(InternalCallerHeaders.SECRET, INTERNAL_SECRET))
        .andExpect(status().isForbidden());
  }

  @Test
  void allowsInternalCallWithValidCredentials() throws Exception {
    mockMvc
        .perform(
            get(ALLOWED_INTERNAL_PATH)
                .header(InternalCallerHeaders.CALLER, INTERNAL_CALLER)
                .header(InternalCallerHeaders.SECRET, INTERNAL_SECRET))
        .andExpect(status().isOk());
  }

  @Test
  void doesNotLetAuthenticatedUserBypassInternalSecret() throws Exception {
    mockMvc
        .perform(get(ALLOWED_INTERNAL_PATH).with(user("probe").roles(ADMIN)))
        .andExpect(status().isForbidden());
  }

  private static Stream<Arguments> roleMatrix() {
    return Stream.of(
            expectations(ADMIN_ONLY_PATHS, Set.of(ADMIN)),
            expectations(LAWYER_AND_ADMIN_PATHS, Set.of(LAWYER, ADMIN)),
            expectations(AUTHENTICATED_PATHS, Set.copyOf(ALL_ROLES)))
        .flatMap(stream -> stream);
  }

  private static Stream<Arguments> expectations(List<String> paths, Set<String> allowedRoles) {
    return paths.stream()
        .flatMap(
            path ->
                ALL_ROLES.stream()
                    .map(role -> arguments(path, role, allowedRoles.contains(role) ? 200 : 403)));
  }

  private static Stream<String> protectedPaths() {
    return Stream.of(ADMIN_ONLY_PATHS, LAWYER_AND_ADMIN_PATHS, AUTHENTICATED_PATHS)
        .flatMap(List::stream);
  }

  private static Stream<String> publicPaths() {
    return PUBLIC_PATHS.stream();
  }

  @Configuration
  @EnableWebMvc
  @Import(SecurityConfigTest.ProbeController.class)
  static class TestWebConfig {

    @Bean
    JwtProperties jwtProperties() {
      return new JwtProperties(null, generateRsaPublicKey(), 900_000L, 2_592_000_000L);
    }

    @Bean
    InternalCallerProperties internalCallerProperties() {
      return new InternalCallerProperties(
          Map.of(INTERNAL_CALLER, new Caller(INTERNAL_SECRET, List.of("/internal/users"))));
    }

    @Bean
    TokenDenylistService tokenDenylistService() {
      return Mockito.mock(TokenDenylistService.class);
    }

    private static String generateRsaPublicKey() {
      try {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return Base64.getEncoder()
            .encodeToString(generator.generateKeyPair().getPublic().getEncoded());
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("RSA is not available", e);
      }
    }
  }

  @RestController
  static class ProbeController {

    @RequestMapping("/**")
    String anyPath() {
      return "ok";
    }
  }
}
