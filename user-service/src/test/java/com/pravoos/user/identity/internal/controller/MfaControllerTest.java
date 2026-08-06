package com.pravoos.user.identity.internal.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.identity.internal.dto.MfaCodeRequest;
import com.pravoos.user.identity.internal.dto.MfaSetupResponse;
import com.pravoos.user.identity.internal.dto.MfaStatusResponse;
import com.pravoos.user.identity.internal.service.MfaService;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import com.pravoos.user.shared.exception.MfaException;
import com.pravoos.user.shared.exception.ProfileNotFoundException;
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
class MfaControllerTest {

  @Mock private MfaService mfaService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    MfaController controller = new MfaController(mfaService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void statusReturnsCurrentMfaStatus() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(mfaService.status(userId)).thenReturn(new MfaStatusResponse(true, false));

    mockMvc
        .perform(get("/api/user/mfa").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.mandatory").value(false));
  }

  @Test
  void statusReturns404WhenUserProfileMissing() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(mfaService.status(userId)).thenThrow(new ProfileNotFoundException());

    mockMvc
        .perform(get("/api/user/mfa").principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void setupReturnsSecretAndOtpAuthUri() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(mfaService.setup(userId)).thenReturn(new MfaSetupResponse("SECRET", "otpauth://totp/x"));

    mockMvc
        .perform(post("/api/user/mfa/setup").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.secret").value("SECRET"))
        .andExpect(jsonPath("$.otpauthUri").value("otpauth://totp/x"));
  }

  @Test
  void setupReturns409WhenAlreadyEnabled() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(mfaService.setup(userId)).thenThrow(MfaException.alreadyEnabled());

    mockMvc
        .perform(post("/api/user/mfa/setup").principal(authentication))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MFA_ALREADY_ENABLED"));
  }

  @Test
  void enableReturns204OnSuccess() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());

    mockMvc
        .perform(
            post("/api/user/mfa/enable")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MfaCodeRequest("123456"))))
        .andExpect(status().isNoContent());

    verify(mfaService).enable(userId, "123456");
  }

  @Test
  void enableReturns401ForInvalidCode() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    org.mockito.Mockito.doThrow(MfaException.invalidCode())
        .when(mfaService)
        .enable(eq(userId), eq("000000"));

    mockMvc
        .perform(
            post("/api/user/mfa/enable")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MfaCodeRequest("000000"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("MFA_INVALID_CODE"));
  }

  @Test
  void enableReturns400ForMalformedCode() throws Exception {
    mockMvc
        .perform(
            post("/api/user/mfa/enable")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MfaCodeRequest("abc"))))
        .andExpect(status().isBadRequest());

    verify(mfaService, never()).enable(eq(userId), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void disableReturns204OnSuccess() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());

    mockMvc
        .perform(
            post("/api/user/mfa/disable")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MfaCodeRequest("123456"))))
        .andExpect(status().isNoContent());

    verify(mfaService).disable(userId, "123456");
  }

  @Test
  void disableReturns403WhenMandatoryForRole() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    org.mockito.Mockito.doThrow(MfaException.mandatory())
        .when(mfaService)
        .disable(eq(userId), eq("123456"));

    mockMvc
        .perform(
            post("/api/user/mfa/disable")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MfaCodeRequest("123456"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MFA_MANDATORY"));
  }

  @Test
  void disableReturns409WhenNotEnabled() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    org.mockito.Mockito.doThrow(MfaException.notEnabled())
        .when(mfaService)
        .disable(eq(userId), eq("123456"));

    mockMvc
        .perform(
            post("/api/user/mfa/disable")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MfaCodeRequest("123456"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MFA_NOT_ENABLED"));
  }
}
