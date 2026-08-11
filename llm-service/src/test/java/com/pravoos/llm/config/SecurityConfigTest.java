package com.pravoos.llm.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.common.security.internal.InternalCallerHeaders;
import com.pravoos.common.security.internal.InternalCallerProperties;
import com.pravoos.common.security.internal.InternalCallerProperties.Caller;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
 * llm-service is reachable only service-to-service: everything outside the actuator, the API docs
 * and {@code /internal/**} is denyAll, and {@code /internal/**} is guarded solely by {@code
 * InternalSecretFilter}. Both halves of that contract are pinned here because a change in
 * authorization defaults would turn the LLM endpoints into an open proxy.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {SecurityConfigTest.TestWebConfig.class, SecurityConfig.class})
class SecurityConfigTest {

  private static final String INTERNAL_CALLER = "ai-service";
  private static final String INTERNAL_SECRET = "internal-secret-value";
  private static final String ALLOWED_INTERNAL_PATH = "/internal/llm/complete";
  private static final String FORBIDDEN_INTERNAL_PATH = "/internal/admin/keys";

  private static final List<String> PUBLIC_PATHS =
      List.of(
          "/actuator/health",
          "/actuator/health/readiness",
          "/actuator/prometheus",
          "/v3/api-docs/swagger-config",
          "/swagger-ui/index.html",
          "/swagger-ui.html");

  private static final List<String> DENIED_PATHS =
      List.of("/api/llm/complete", "/actuator/env", "/actuator/heapdump", "/anything-else");

  private MockMvc mockMvc;

  @BeforeEach
  void setUp(WebApplicationContext webApplicationContext) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @ParameterizedTest(name = "anonymous on {0} -> 200")
  @MethodSource("publicPaths")
  void allowsAnonymousOnPublicRoutes(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isOk());
  }

  @ParameterizedTest(name = "anonymous on {0} -> denied")
  @MethodSource("deniedPaths")
  void deniesAnonymousEverywhereElse(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isForbidden());
  }

  @ParameterizedTest(name = "authenticated user on {0} -> denied")
  @MethodSource("deniedPaths")
  void deniesAuthenticatedUsersEverywhereElse(String path) throws Exception {
    mockMvc.perform(get(path).with(user("probe").roles("ADMIN"))).andExpect(status().isForbidden());
  }

  @Test
  void rejectsInternalCallWithoutCredentials() throws Exception {
    mockMvc.perform(post(ALLOWED_INTERNAL_PATH)).andExpect(status().isForbidden());
  }

  @Test
  void rejectsInternalCallWithWrongSecret() throws Exception {
    mockMvc
        .perform(
            post(ALLOWED_INTERNAL_PATH)
                .header(InternalCallerHeaders.CALLER, INTERNAL_CALLER)
                .header(InternalCallerHeaders.SECRET, "wrong-secret"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsInternalCallFromUnknownCaller() throws Exception {
    mockMvc
        .perform(
            post(ALLOWED_INTERNAL_PATH)
                .header(InternalCallerHeaders.CALLER, "attacker")
                .header(InternalCallerHeaders.SECRET, INTERNAL_SECRET))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsInternalCallToPathOutsideCallerAllowlist() throws Exception {
    mockMvc
        .perform(
            post(FORBIDDEN_INTERNAL_PATH)
                .header(InternalCallerHeaders.CALLER, INTERNAL_CALLER)
                .header(InternalCallerHeaders.SECRET, INTERNAL_SECRET))
        .andExpect(status().isForbidden());
  }

  @Test
  void allowsInternalCallWithValidCredentials() throws Exception {
    mockMvc
        .perform(
            post(ALLOWED_INTERNAL_PATH)
                .header(InternalCallerHeaders.CALLER, INTERNAL_CALLER)
                .header(InternalCallerHeaders.SECRET, INTERNAL_SECRET))
        .andExpect(status().isOk());
  }

  private static Stream<String> publicPaths() {
    return PUBLIC_PATHS.stream();
  }

  private static Stream<String> deniedPaths() {
    return DENIED_PATHS.stream();
  }

  @Configuration
  @EnableWebMvc
  @Import(SecurityConfigTest.ProbeController.class)
  static class TestWebConfig {

    @Bean
    InternalCallerProperties internalCallerProperties() {
      return new InternalCallerProperties(
          Map.of(INTERNAL_CALLER, new Caller(INTERNAL_SECRET, List.of("/internal/llm"))));
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
