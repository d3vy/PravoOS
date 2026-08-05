package com.pravoos.user.identity.internal.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.identity.api.PasswordPolicyService;
import com.pravoos.user.identity.internal.dto.LoginRequest;
import com.pravoos.user.identity.internal.dto.LoginResult;
import com.pravoos.user.identity.internal.dto.TokenResponse;
import com.pravoos.user.identity.internal.security.RefreshCookieFactory;
import com.pravoos.user.identity.internal.service.AuthService;
import com.pravoos.user.identity.internal.service.PasswordResetService;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.shared.exception.AccountLockedException;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import com.pravoos.user.shared.exception.InvalidCredentialsException;
import com.pravoos.user.shared.exception.InvalidRefreshTokenException;
import com.pravoos.user.shared.service.IpRateLimiter;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  private static final String EMAIL = "lawyer@example.com";
  private static final String PASSWORD = "secret123";

  @Mock private AuthService authService;
  @Mock private RefreshCookieFactory refreshCookieFactory;
  @Mock private PasswordResetService passwordResetService;
  @Mock private PasswordPolicyService passwordPolicyService;
  @Mock private IpRateLimiter ipRateLimiter;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    AuthController controller =
        new AuthController(
            authService,
            refreshCookieFactory,
            passwordResetService,
            passwordPolicyService,
            ipRateLimiter);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    lenient()
        .when(
            ipRateLimiter.allow(
                anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any()))
        .thenReturn(true);
    lenient()
        .when(refreshCookieFactory.create(anyString()))
        .thenReturn(ResponseCookie.from(RefreshCookieFactory.COOKIE_NAME, "cookie-value").build());
    lenient()
        .when(refreshCookieFactory.clear())
        .thenReturn(ResponseCookie.from(RefreshCookieFactory.COOKIE_NAME, "").maxAge(0).build());
  }

  @Test
  void loginReturnsAccessTokenAndSetsRefreshCookieOnSuccess() throws Exception {
    UUID userId = UUID.randomUUID();
    TokenResponse tokens =
        new TokenResponse("access-token", "refresh-raw", userId, EMAIL, UserRole.LAWYER);
    when(authService.login(any(LoginRequest.class), anyString(), any()))
        .thenReturn(LoginResult.success(tokens));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mfaRequired").value(false))
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(header().exists("Set-Cookie"));

    verify(refreshCookieFactory).create("refresh-raw");
  }

  @Test
  void loginReturnsMfaChallengeWithoutSettingCookie() throws Exception {
    when(authService.login(any(LoginRequest.class), anyString(), any()))
        .thenReturn(LoginResult.mfaRequired("mfa-token"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mfaRequired").value(true))
        .andExpect(jsonPath("$.mfaToken").value("mfa-token"))
        .andExpect(jsonPath("$.accessToken").doesNotExist());

    verify(refreshCookieFactory, never()).create(anyString());
  }

  @Test
  void loginReturns401ForInvalidCredentials() throws Exception {
    when(authService.login(any(LoginRequest.class), anyString(), any()))
        .thenThrow(new InvalidCredentialsException());

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, "wrong"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid email or password"));
  }

  @Test
  void loginReturns429WithRetryAfterHeaderForLockedAccount() throws Exception {
    when(authService.login(any(LoginRequest.class), anyString(), any()))
        .thenThrow(new AccountLockedException(120L));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "120"));
  }

  @Test
  void loginReturns429WhenIpRateLimitExceeded() throws Exception {
    when(ipRateLimiter.allow(
            anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any()))
        .thenReturn(false);

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
        .andExpect(status().isTooManyRequests());

    verify(authService, never()).login(any(), anyString(), any());
  }

  @Test
  void loginReturns400ForInvalidRequestBody() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("not-an-email", ""))))
        .andExpect(status().isBadRequest());

    verify(authService, never()).login(any(), anyString(), any());
  }

  @Test
  void refreshIssuesNewTokensWhenCookiePresent() throws Exception {
    UUID userId = UUID.randomUUID();
    TokenResponse tokens =
        new TokenResponse("new-access", "new-refresh", userId, EMAIL, UserRole.LAWYER);
    when(authService.refresh(eq("old-refresh"), anyString(), any())).thenReturn(tokens);

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, "old-refresh")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-access"))
        .andExpect(header().exists("Set-Cookie"));
  }

  @Test
  void refreshReturns401WhenCookieMissing() throws Exception {
    mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());

    verify(authService, never()).refresh(anyString(), anyString(), any());
  }

  @Test
  void refreshReturns401ForInvalidToken() throws Exception {
    when(authService.refresh(eq("bad-refresh"), anyString(), any()))
        .thenThrow(new InvalidRefreshTokenException());

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, "bad-refresh")))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(containsString("Invalid or expired refresh token")));
  }

  @Test
  void logoutRevokesRefreshTokenAndClearsCookieWhenPresent() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/logout")
                .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, "raw-refresh")))
        .andExpect(status().isNoContent())
        .andExpect(header().exists("Set-Cookie"));

    verify(authService).logout("raw-refresh");
  }

  @Test
  void logoutIsNoOpWhenCookieMissingButStillClearsCookie() throws Exception {
    mockMvc
        .perform(post("/api/auth/logout"))
        .andExpect(status().isNoContent())
        .andExpect(header().exists("Set-Cookie"));

    verify(authService, never()).logout(anyString());
  }
}
