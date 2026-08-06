package com.pravoos.user.collaboration.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.collaboration.internal.dto.BindTelegramRequest;
import com.pravoos.user.collaboration.internal.service.TelegramLinkService;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import com.pravoos.user.shared.exception.InvalidTelegramLinkCodeException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TelegramInternalControllerTest {

  @Mock private TelegramLinkService telegramLinkService;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    TelegramInternalController controller = new TelegramInternalController(telegramLinkService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void bindReturnsFullNameOnSuccess() throws Exception {
    when(telegramLinkService.bind("abc123", 555L)).thenReturn("Иван Иванов");

    mockMvc
        .perform(
            post("/internal/telegram/bind")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BindTelegramRequest("abc123", 555L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Иван Иванов"));
  }

  @Test
  void bindReturns404ForInvalidCode() throws Exception {
    when(telegramLinkService.bind("bad-code", 555L))
        .thenThrow(new InvalidTelegramLinkCodeException());

    mockMvc
        .perform(
            post("/internal/telegram/bind")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new BindTelegramRequest("bad-code", 555L))))
        .andExpect(status().isNotFound());
  }

  @Test
  void bindReturns400ForBlankCodeInBody() throws Exception {
    mockMvc
        .perform(
            post("/internal/telegram/bind")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BindTelegramRequest("", 555L))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void chatIdReturnsChatIdWhenLinked() throws Exception {
    UUID lawyerId = UUID.randomUUID();
    when(telegramLinkService.resolveChatId(lawyerId)).thenReturn(Optional.of(777L));

    mockMvc
        .perform(get("/internal/telegram/chat-id/{lawyerId}", lawyerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chatId").value(777));
  }

  @Test
  void chatIdReturns404WhenNotLinked() throws Exception {
    UUID lawyerId = UUID.randomUUID();
    when(telegramLinkService.resolveChatId(lawyerId)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/internal/telegram/chat-id/{lawyerId}", lawyerId))
        .andExpect(status().isNotFound());
  }
}
