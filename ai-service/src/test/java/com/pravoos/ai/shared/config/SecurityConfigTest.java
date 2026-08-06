package com.pravoos.ai.shared.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.shared.security.AccessTokenDenylist;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {SecurityConfigTest.TestWebConfig.class, SecurityConfig.class})
class SecurityConfigTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp(WebApplicationContext webApplicationContext) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  void healthCheckIsPubliclyAccessible() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void invoiceWebhookIsPubliclyAccessible() throws Exception {
    mockMvc.perform(post("/api/ai/billing/invoice-webhook")).andExpect(status().isOk());
  }

  @Test
  void unauthenticatedRequestToAdminEndpointIsRejected() throws Exception {
    mockMvc.perform(get("/api/ai/admin/lawyers")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "LAWYER")
  void lawyerCannotAccessAdminEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/admin/lawyers")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void adminCanAccessAdminEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/admin/lawyers")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void adminCanAccessDocumentsEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/documents/1")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "LAWYER")
  void lawyerCannotAccessDocumentsEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/documents/1")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "LAWYER")
  void lawyerCanAccessDocumentInsightsEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/document-insights/1")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void adminCanAccessDocumentInsightsEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/document-insights/1")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "CLIENT")
  void clientCannotAccessDocumentInsightsEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/document-insights/1")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "CLIENT")
  void clientCanAccessPortalEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/portal/cases")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "LAWYER")
  void lawyerCannotAccessPortalEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/portal/cases")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "LAWYER")
  void lawyerCanAccessCasesEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/cases/1")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "CLIENT")
  void clientCannotAccessCasesEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/cases/1")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "LAWYER")
  void lawyerCanAccessBillingProfileEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/billing-profile")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "CLIENT")
  void clientCannotAccessBillingProfileEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/billing-profile")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "LAWYER")
  void lawyerCanAccessSavedViewsEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/saved-views")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "CLIENT")
  void clientCannotAccessSavedViewsEndpoint() throws Exception {
    mockMvc.perform(get("/api/ai/saved-views")).andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedRequestToUnmatchedEndpointIsRejected() throws Exception {
    mockMvc.perform(get("/api/ai/whatever")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "CLIENT")
  void authenticatedRequestToUnmatchedEndpointIsAllowedRegardlessOfRole() throws Exception {
    mockMvc.perform(get("/api/ai/whatever")).andExpect(status().isOk());
  }

  @Configuration
  @EnableWebMvc
  @Import(SecurityConfigTest.ProbeController.class)
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

    @GetMapping("/actuator/health")
    String health() {
      return "ok";
    }

    @PostMapping("/api/ai/billing/invoice-webhook")
    String invoiceWebhook() {
      return "ok";
    }

    @GetMapping("/api/ai/admin/lawyers")
    String admin() {
      return "ok";
    }

    @GetMapping("/api/ai/documents/{id}")
    String documents() {
      return "ok";
    }

    @GetMapping("/api/ai/document-insights/{id}")
    String documentInsights() {
      return "ok";
    }

    @GetMapping("/api/ai/portal/cases")
    String portal() {
      return "ok";
    }

    @GetMapping("/api/ai/cases/{id}")
    String cases() {
      return "ok";
    }

    @GetMapping("/api/ai/billing-profile")
    String billingProfile() {
      return "ok";
    }

    @GetMapping("/api/ai/saved-views")
    String savedViews() {
      return "ok";
    }

    @GetMapping("/api/ai/whatever")
    String whatever() {
      return "ok";
    }
  }
}
