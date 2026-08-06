package com.pravoos.user.collaboration.internal.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.collaboration.internal.dto.CaseMessageNotificationRequest;
import com.pravoos.user.collaboration.internal.dto.CaseMessageNotificationResult;
import com.pravoos.user.collaboration.internal.dto.DeadlineEmailRequest;
import com.pravoos.user.collaboration.internal.service.CaseMessageNotificationService;
import com.pravoos.user.collaboration.internal.service.DeadlineNotificationEmailService;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
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
class InternalNotificationControllerTest {

  @Mock private DeadlineNotificationEmailService deadlineNotificationEmailService;
  @Mock private CaseMessageNotificationService caseMessageNotificationService;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    InternalNotificationController controller =
        new InternalNotificationController(
            deadlineNotificationEmailService, caseMessageNotificationService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void deadlineEmail_returns400ForInvalidBody() throws Exception {
    mockMvc
        .perform(
            post("/internal/notifications/deadline-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deadlineEmail_delegatesToService() throws Exception {
    DeadlineEmailRequest request =
        new DeadlineEmailRequest(
            UUID.randomUUID(), UUID.randomUUID(), "Дело", "Подача иска", "2026-08-10", 3);

    mockMvc
        .perform(
            post("/internal/notifications/deadline-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(deadlineNotificationEmailService).sendDeadlineEmail(eq(request));
  }

  @Test
  void caseMessage_returns400ForInvalidBody() throws Exception {
    mockMvc
        .perform(
            post("/internal/notifications/case-message")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void caseMessage_delegatesToServiceAndReturnsResult() throws Exception {
    CaseMessageNotificationRequest request =
        new CaseMessageNotificationRequest(
            UUID.randomUUID(), "Дело №1", "LAWYER", UUID.randomUUID(), null, "Здравствуйте");
    UUID recipientUserId = UUID.randomUUID();
    when(caseMessageNotificationService.dispatch(eq(request)))
        .thenReturn(new CaseMessageNotificationResult(recipientUserId, 42L, true));

    mockMvc
        .perform(
            post("/internal/notifications/case-message")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recipientUserId").value(recipientUserId.toString()))
        .andExpect(jsonPath("$.telegramChatId").value(42))
        .andExpect(jsonPath("$.pushEnabled").value(true));
  }
}
