package com.pravoos.ai.practice.internal.controller;

import static org.hamcrest.Matchers.hasSize;
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
import com.pravoos.common.web.OrgContext;
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
class PortalMessageControllerTest {

  @Mock private CaseMessageService caseMessageService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID clientId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    PortalMessageController controller = new PortalMessageController(caseMessageService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void listReturnsClientThread() throws Exception {
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID messageId = UUID.randomUUID();
    when(caseMessageService.findClientThread(caseId, List.of(clientId)))
        .thenReturn(
            List.of(
                new CaseMessageResponse(
                    messageId,
                    userId,
                    MessageAuthorRole.CLIENT,
                    "Hello",
                    LocalDateTime.now(ZoneOffset.UTC))));

    mockMvc
        .perform(get("/api/ai/portal/cases/{caseId}/messages", caseId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].body").value("Hello"));
  }

  @Test
  void listReturns404WhenCaseNotVisible() throws Exception {
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    when(caseMessageService.findClientThread(caseId, List.of(clientId)))
        .thenThrow(new CaseNotFoundException(caseId));

    mockMvc
        .perform(get("/api/ai/portal/cases/{caseId}/messages", caseId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void sendReturns201WithCreatedMessage() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID messageId = UUID.randomUUID();
    SendMessageRequest request = new SendMessageRequest("Hello lawyer");
    when(caseMessageService.postClientMessage(caseId, "Hello lawyer", userId, List.of(clientId)))
        .thenReturn(
            new CaseMessageResponse(
                messageId,
                userId,
                MessageAuthorRole.CLIENT,
                "Hello lawyer",
                LocalDateTime.now(ZoneOffset.UTC)));

    mockMvc
        .perform(
            post("/api/ai/portal/cases/{caseId}/messages", caseId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(messageId.toString()));
  }

  @Test
  void sendReturns400OnBlankBody() throws Exception {
    SendMessageRequest request = new SendMessageRequest(" ");

    mockMvc
        .perform(
            post("/api/ai/portal/cases/{caseId}/messages", caseId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
