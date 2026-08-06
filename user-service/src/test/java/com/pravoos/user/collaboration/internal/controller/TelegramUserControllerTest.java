package com.pravoos.user.collaboration.internal.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.user.collaboration.internal.dto.TelegramLinkResponse;
import com.pravoos.user.collaboration.internal.service.TelegramLinkService;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import com.pravoos.user.shared.exception.ProfileNotFoundException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TelegramUserControllerTest {

  @Mock private TelegramLinkService telegramLinkService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TelegramUserController controller = new TelegramUserController(telegramLinkService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void createTelegramLinkCodeReturnsCode() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
    when(telegramLinkService.createLinkCode(userId))
        .thenReturn(new TelegramLinkResponse("abc123", "https://t.me/bot?start=abc123", expiresAt));

    mockMvc
        .perform(post("/api/user/profile/telegram/link-code").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("abc123"))
        .andExpect(jsonPath("$.deepLink").value("https://t.me/bot?start=abc123"));
  }

  @Test
  void createTelegramLinkCodeReturns404WhenProfileMissing() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(telegramLinkService.createLinkCode(userId)).thenThrow(new ProfileNotFoundException());

    mockMvc
        .perform(post("/api/user/profile/telegram/link-code").principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void unlinkTelegramReturns204AndDelegatesToService() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());

    mockMvc
        .perform(delete("/api/user/profile/telegram").principal(authentication))
        .andExpect(status().isNoContent());

    verify(telegramLinkService).unlink(userId);
  }
}
