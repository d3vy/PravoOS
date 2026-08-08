package com.pravoos.ai.practice.internal.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.practice.internal.dto.CaseMessageResponse;
import com.pravoos.ai.practice.internal.dto.SendMessageRequest;
import com.pravoos.ai.practice.internal.service.CaseMessageService;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.MessageAuthorRole;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
class CaseMessageControllerTest {

  @Mock private CaseMessageService caseMessageService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    CaseMessageController controller = new CaseMessageController(caseMessageService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void listReturnsLawyerThread() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(caseMessageService.findLawyerThread(eq(caseId), eq(lawyerId), anyList()))
        .thenReturn(
            List.of(
                new CaseMessageResponse(
                    UUID.randomUUID(),
                    lawyerId,
                    MessageAuthorRole.LAWYER,
                    "Здравствуйте",
                    LocalDateTime.now(ZoneOffset.UTC))));

    mockMvc
        .perform(get("/api/ai/cases/{caseId}/messages", caseId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].body").value("Здравствуйте"));
  }

  @Test
  void listReturnsNotFoundForUnknownCase() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(caseMessageService.findLawyerThread(eq(caseId), eq(lawyerId), anyList()))
        .thenThrow(new CaseNotFoundException(caseId));

    mockMvc
        .perform(get("/api/ai/cases/{caseId}/messages", caseId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void markReadReturnsNoContent() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());

    mockMvc
        .perform(post("/api/ai/cases/{caseId}/messages/read", caseId).principal(authentication))
        .andExpect(status().isNoContent());
    verify(caseMessageService).markThreadRead(eq(caseId), eq(lawyerId), anyList());
  }

  @Test
  void sendReturnsCreatedMessage() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID messageId = UUID.randomUUID();
    when(caseMessageService.postLawyerMessage(
            eq(caseId), eq("Ответ по делу"), eq(lawyerId), anyList()))
        .thenReturn(
            new CaseMessageResponse(
                messageId,
                lawyerId,
                MessageAuthorRole.LAWYER,
                "Ответ по делу",
                LocalDateTime.now(ZoneOffset.UTC)));

    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/messages", caseId)
                .principal(authentication)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SendMessageRequest("Ответ по делу"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(messageId.toString()));
  }

  @Test
  void sendRejectsBlankBody() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/messages", caseId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SendMessageRequest(""))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(caseMessageService);
  }
}
