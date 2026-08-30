package com.pravoos.ai.practice.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.practice.internal.dto.BillingProfileRequest;
import com.pravoos.ai.practice.internal.dto.BillingProfileResponse;
import com.pravoos.ai.practice.internal.service.BillingProfileService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.security.CallerContextArgumentResolver;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BillingProfileControllerTest {

  @Mock private BillingProfileService billingProfileService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    BillingProfileController controller = new BillingProfileController(billingProfileService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void getReturnsProfileWhenPresent() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(billingProfileService.find(lawyerId))
        .thenReturn(
            Optional.of(
                new BillingProfileResponse(
                    "ООО Ромашка",
                    "770123456789",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    LocalDateTime.now(ZoneOffset.UTC))));

    mockMvc
        .perform(get("/api/ai/billing-profile").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("ООО Ромашка"));
  }

  @Test
  void getReturns204WhenAbsent() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(billingProfileService.find(lawyerId)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/ai/billing-profile").principal(authentication))
        .andExpect(status().isNoContent());
  }

  @Test
  void saveReturnsUpdatedProfile() throws Exception {
    BillingProfileRequest request =
        new BillingProfileRequest(
            "ООО Ромашка", null, null, null, null, null, null, null, null, null, null);
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(billingProfileService.save(request, lawyerId))
        .thenReturn(
            new BillingProfileResponse(
                "ООО Ромашка",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now(ZoneOffset.UTC)));

    mockMvc
        .perform(
            put("/api/ai/billing-profile")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("ООО Ромашка"));
  }

  @Test
  void saveReturns400WhenNameBlank() throws Exception {
    BillingProfileRequest request =
        new BillingProfileRequest(" ", null, null, null, null, null, null, null, null, null, null);

    mockMvc
        .perform(
            put("/api/ai/billing-profile")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
