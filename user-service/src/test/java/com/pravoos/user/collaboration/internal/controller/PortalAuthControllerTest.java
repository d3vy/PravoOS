package com.pravoos.user.collaboration.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.collaboration.internal.dto.PortalAcceptRequest;
import com.pravoos.user.collaboration.internal.dto.PortalInvitePreviewResponse;
import com.pravoos.user.collaboration.internal.service.ClientPortalInviteService;
import com.pravoos.user.identity.api.AuthTokens;
import com.pravoos.user.identity.api.LoginResponse;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import com.pravoos.user.shared.exception.InvalidInviteException;
import com.pravoos.user.shared.service.IpRateLimiter;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PortalAuthControllerTest {

  @Mock private ClientPortalInviteService clientPortalInviteService;
  @Mock private AuthTokens authTokens;
  @Mock private IpRateLimiter ipRateLimiter;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    PortalAuthController controller =
        new PortalAuthController(clientPortalInviteService, authTokens, ipRateLimiter);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void portalInvitePreviewReturnsPreview() throws Exception {
    when(clientPortalInviteService.preview("tok-123"))
        .thenReturn(
            new PortalInvitePreviewResponse("client@example.com", "Клиент Клиентов", false));

    mockMvc
        .perform(get("/api/auth/portal/invite").param("token", "tok-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("client@example.com"))
        .andExpect(jsonPath("$.accountExists").value(false));
  }

  @Test
  void portalInvitePreviewReturns500WhenTokenParamMissing() throws Exception {
    // GlobalExceptionHandler не мапит MissingServletRequestParameterException — падает в generic
    // 500-обработчик
    mockMvc.perform(get("/api/auth/portal/invite")).andExpect(status().isInternalServerError());
  }

  @Test
  void acceptPortalInviteAuthenticatesUserOnSuccess() throws Exception {
    when(ipRateLimiter.allow(eq("portal-accept"), anyString(), eq(5), any(Duration.class)))
        .thenReturn(true);
    UUID userId = UUID.randomUUID();
    when(clientPortalInviteService.accept("tok-123", "password1")).thenReturn(userId);
    when(authTokens.authenticate(eq(userId), anyString(), any()))
        .thenReturn(
            ResponseEntity.ok(
                LoginResponse.authenticated(
                    new com.pravoos.user.identity.api.AuthResponse(
                        "access-token", userId, "client@example.com", UserRole.CLIENT))));

    mockMvc
        .perform(
            post("/api/auth/portal/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new PortalAcceptRequest("tok-123", "password1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"));
  }

  @Test
  void acceptPortalInviteReturns429WhenIpRateLimitExceeded() throws Exception {
    when(ipRateLimiter.allow(eq("portal-accept"), anyString(), eq(5), any(Duration.class)))
        .thenReturn(false);

    mockMvc
        .perform(
            post("/api/auth/portal/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new PortalAcceptRequest("tok-123", "password1"))))
        .andExpect(status().isTooManyRequests());

    verify(clientPortalInviteService, never()).accept(anyString(), anyString());
  }

  @Test
  void acceptPortalInviteReturns400ForBlankFields() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/portal/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PortalAcceptRequest("", ""))))
        .andExpect(status().isBadRequest());

    verify(ipRateLimiter, never())
        .allow(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
  }

  @Test
  void acceptPortalInviteReturns400ForInvalidInviteToken() throws Exception {
    when(ipRateLimiter.allow(eq("portal-accept"), anyString(), eq(5), any(Duration.class)))
        .thenReturn(true);
    when(clientPortalInviteService.accept("bad-token", "password1"))
        .thenThrow(new InvalidInviteException());

    mockMvc
        .perform(
            post("/api/auth/portal/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new PortalAcceptRequest("bad-token", "password1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INVITE"));
  }
}
