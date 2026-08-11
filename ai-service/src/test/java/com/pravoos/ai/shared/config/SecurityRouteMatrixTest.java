package com.pravoos.ai.shared.config;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.shared.security.AccessTokenDenylist;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
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
 * Exhaustive role matrix over every matcher declared in {@link SecurityConfig}. Unlike the
 * hand-written cases in {@code SecurityConfigTest}, this covers each route prefix against every
 * role, so a change in framework authorization defaults cannot silently widen or narrow access
 * without a red build.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {SecurityRouteMatrixTest.TestWebConfig.class, SecurityConfig.class})
class SecurityRouteMatrixTest {

  private static final String LAWYER = "LAWYER";
  private static final String ADMIN = "ADMIN";
  private static final String CLIENT = "CLIENT";
  private static final List<String> ALL_ROLES = List.of(LAWYER, ADMIN, CLIENT);

  private static final List<String> PUBLIC_PATHS =
      List.of(
          "/actuator/health",
          "/actuator/health/readiness",
          "/actuator/prometheus",
          "/v3/api-docs/swagger-config",
          "/swagger-ui/index.html",
          "/swagger-ui.html");

  private static final List<String> ADMIN_ONLY_PATHS =
      List.of("/api/ai/admin/lawyers", "/api/ai/documents/1");

  private static final List<String> CLIENT_ONLY_PATHS = List.of("/api/ai/portal/cases");

  private static final List<String> LAWYER_AND_ADMIN_PATHS =
      List.of("/api/ai/document-insights/1", "/api/ai/unmatched-by-any-rule");

  private static final List<String> LAWYER_ONLY_PATHS =
      List.of(
          "/api/ai/cases/1",
          "/api/ai/dashboard/summary",
          "/api/ai/calendar/events",
          "/api/ai/clients/1",
          "/api/ai/templates/1",
          "/api/ai/search/query",
          "/api/ai/drafts/1",
          "/api/ai/draft-types",
          "/api/ai/workflows/1",
          "/api/ai/workflow-definitions/1",
          "/api/ai/contract-reviews/1",
          "/api/ai/document-comparisons/1",
          "/api/ai/tabular-reviews/1",
          "/api/ai/citation-checks/1",
          "/api/ai/responses/1",
          "/api/ai/messages/1",
          "/api/ai/conversations/1",
          "/api/ai/chat/stream",
          "/api/ai/invoices/1",
          "/api/ai/billing-profile/current",
          "/api/ai/saved-views/1",
          "/api/ai/time/entries",
          "/api/ai/mailboxes/1",
          "/api/ai/emails/1");

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
  void allowsAnonymousInvoiceWebhook() throws Exception {
    mockMvc.perform(post("/api/ai/billing/invoice-webhook")).andExpect(status().isOk());
  }

  private static Stream<Arguments> roleMatrix() {
    return Stream.of(
            expectations(ADMIN_ONLY_PATHS, Set.of(ADMIN)),
            expectations(CLIENT_ONLY_PATHS, Set.of(CLIENT)),
            expectations(LAWYER_AND_ADMIN_PATHS, Set.of(LAWYER, ADMIN)),
            expectations(LAWYER_ONLY_PATHS, Set.of(LAWYER)))
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
    return Stream.of(ADMIN_ONLY_PATHS, CLIENT_ONLY_PATHS, LAWYER_AND_ADMIN_PATHS, LAWYER_ONLY_PATHS)
        .flatMap(List::stream);
  }

  private static Stream<String> publicPaths() {
    return PUBLIC_PATHS.stream();
  }

  @Configuration
  @EnableWebMvc
  @Import(SecurityRouteMatrixTest.ProbeController.class)
  static class TestWebConfig {

    @Bean
    JwtProperties jwtProperties() {
      return new JwtProperties(generateRsaPublicKey());
    }

    @Bean
    AccessTokenDenylist accessTokenDenylist() {
      return Mockito.mock(AccessTokenDenylist.class);
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
