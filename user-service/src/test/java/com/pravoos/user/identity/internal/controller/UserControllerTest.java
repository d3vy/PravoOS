package com.pravoos.user.identity.internal.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.identity.api.LawyerProfileResponse;
import com.pravoos.user.identity.internal.dto.LanguageSettingsResponse;
import com.pravoos.user.identity.internal.dto.NotificationSettingsResponse;
import com.pravoos.user.identity.internal.dto.SessionResponse;
import com.pravoos.user.identity.internal.dto.UpdateLanguageRequest;
import com.pravoos.user.identity.internal.dto.UpdateNotificationSettingsRequest;
import com.pravoos.user.identity.internal.dto.UpdateProfileRequest;
import com.pravoos.user.identity.internal.security.JwtAuthenticationFilter;
import com.pravoos.user.identity.internal.service.RefreshTokenService;
import com.pravoos.user.identity.internal.service.UserService;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
class UserControllerTest {

  @Mock private UserService userService;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    UserController controller = new UserController(userService, refreshTokenService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void getProfileReturnsCurrentUserProfile() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(userService.getProfile(userId))
        .thenReturn(
            new LawyerProfileResponse(
                userId,
                "lawyer@example.com",
                "Иван Иванов",
                "Уголовное право",
                "+79990000000",
                true));

    mockMvc
        .perform(get("/api/user/profile").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("lawyer@example.com"))
        .andExpect(jsonPath("$.telegramLinked").value(true));
  }

  @Test
  void updateProfileReturns400ForBlankFullName() throws Exception {
    mockMvc
        .perform(
            patch("/api/user/profile")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateProfileRequest("", null, null))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateProfileDelegatesToServiceAndReturnsUpdatedProfile() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UpdateProfileRequest request =
        new UpdateProfileRequest("Пётр Петров", "Гражданское право", "+79991112233");
    when(userService.updateProfile(eq(userId), eq(request)))
        .thenReturn(
            new LawyerProfileResponse(
                userId,
                "lawyer@example.com",
                "Пётр Петров",
                "Гражданское право",
                "+79991112233",
                false));

    mockMvc
        .perform(
            patch("/api/user/profile")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Пётр Петров"));
  }

  @Test
  void getNotificationSettingsReturnsCurrentSettings() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(userService.getNotificationSettings(userId))
        .thenReturn(
            new NotificationSettingsResponse(true, false, true, false, true, false, true, true));

    mockMvc
        .perform(get("/api/user/settings/notifications").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loginAlertEmail").value(true))
        .andExpect(jsonPath("$.telegramLinked").value(true));
  }

  @Test
  void updateNotificationSettingsReturns400WhenFieldMissing() throws Exception {
    mockMvc
        .perform(
            put("/api/user/settings/notifications")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginAlertEmail\":true}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateNotificationSettingsDelegatesToService() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UpdateNotificationSettingsRequest request =
        new UpdateNotificationSettingsRequest(true, false, true, false, true, false, true);
    when(userService.updateNotificationSettings(eq(userId), eq(request)))
        .thenReturn(
            new NotificationSettingsResponse(true, false, true, false, true, false, true, false));

    mockMvc
        .perform(
            put("/api/user/settings/notifications")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.digestPush").value(true));
  }

  @Test
  void getLanguageReturnsCurrentLanguage() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(userService.getLanguage(userId)).thenReturn(new LanguageSettingsResponse("ru"));

    mockMvc
        .perform(get("/api/user/settings/language").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("ru"));
  }

  @Test
  void updateLanguageReturns400ForUnsupportedLanguage() throws Exception {
    mockMvc
        .perform(
            put("/api/user/settings/language")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateLanguageRequest("fr"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateLanguageDelegatesToService() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(userService.updateLanguage(eq(userId), eq(new UpdateLanguageRequest("en"))))
        .thenReturn(new LanguageSettingsResponse("en"));

    mockMvc
        .perform(
            put("/api/user/settings/language")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateLanguageRequest("en"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("en"));
  }

  @Test
  void listSessionsReturnsActiveSessions() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID sessionId = UUID.randomUUID();
    when(refreshTokenService.listActiveSessions(userId, sessionId))
        .thenReturn(
            List.of(
                new SessionResponse(
                    sessionId,
                    "203.0.113.5",
                    "Mozilla/5.0",
                    LocalDateTime.now(ZoneOffset.UTC),
                    LocalDateTime.now(ZoneOffset.UTC),
                    true)));

    mockMvc
        .perform(
            get("/api/user/sessions")
                .principal(authentication)
                .requestAttr(JwtAuthenticationFilter.SESSION_ID_ATTRIBUTE, sessionId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(sessionId.toString()))
        .andExpect(jsonPath("$[0].current").value(true))
        .andExpect(jsonPath("$[0].ipAddress").value("203.0.113.5"));
  }

  @Test
  void revokeSessionReturns204AndDelegatesToService() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID sessionId = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/user/sessions/{sessionId}", sessionId).principal(authentication))
        .andExpect(status().isNoContent());

    verify(refreshTokenService).revokeSession(userId, sessionId);
  }
}
