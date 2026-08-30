package com.pravoos.ai.practice.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.practice.internal.dto.EmailMessageResponse;
import com.pravoos.ai.practice.internal.dto.LinkEmailRequest;
import com.pravoos.ai.practice.internal.service.EmailLinkingService;
import com.pravoos.ai.shared.exception.EmailLinkTargetRequiredException;
import com.pravoos.ai.shared.exception.EmailMessageNotFoundException;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.EmailDirection;
import com.pravoos.ai.shared.security.CallerContextArgumentResolver;
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
class EmailMessageControllerTest {

  @Mock private EmailLinkingService emailLinkingService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID emailId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    EmailMessageController controller = new EmailMessageController(emailLinkingService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private EmailMessageResponse sampleEmail(UUID caseId, UUID clientId) {
    return new EmailMessageResponse(
        emailId,
        UUID.randomUUID(),
        EmailDirection.IN,
        "client@example.com",
        "lawyer@example.com",
        null,
        "Тема письма",
        "Текст письма",
        LocalDateTime.now(ZoneOffset.UTC),
        false,
        0,
        caseId,
        clientId,
        null,
        null,
        null);
  }

  @Test
  void unlinkedReturnsPagedResults() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(emailLinkingService.findUnlinked(lawyerId, 0, 20))
        .thenReturn(
            new org.springframework.data.domain.PageImpl<>(List.of(sampleEmail(null, null))));

    mockMvc
        .perform(get("/api/ai/emails/unlinked").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(
            header().string(com.pravoos.ai.shared.util.PagedResponse.TOTAL_COUNT_HEADER, "1"))
        .andExpect(jsonPath("$[0].subject").value("Тема письма"));
  }

  @Test
  void linkReturnsLinkedEmail() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID caseId = UUID.randomUUID();
    when(emailLinkingService.link(eq(emailId), any(LinkEmailRequest.class), eq(lawyerId)))
        .thenReturn(sampleEmail(caseId, null));

    mockMvc
        .perform(
            post("/api/ai/emails/{emailId}/link", emailId)
                .principal(authentication)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new LinkEmailRequest(caseId, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseId").value(caseId.toString()));
  }

  @Test
  void linkPropagatesTargetRequiredAsBadRequest() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(emailLinkingService.link(eq(emailId), any(LinkEmailRequest.class), eq(lawyerId)))
        .thenThrow(new EmailLinkTargetRequiredException());

    mockMvc
        .perform(
            post("/api/ai/emails/{emailId}/link", emailId)
                .principal(authentication)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new LinkEmailRequest(null, null))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void linkReturnsNotFoundForUnknownEmail() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(emailLinkingService.link(eq(emailId), any(LinkEmailRequest.class), eq(lawyerId)))
        .thenThrow(new EmailMessageNotFoundException(emailId));

    mockMvc
        .perform(
            post("/api/ai/emails/{emailId}/link", emailId)
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(new LinkEmailRequest(UUID.randomUUID(), null))))
        .andExpect(status().isNotFound());
  }

  @Test
  void unlinkReturnsUnlinkedEmail() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(emailLinkingService.unlink(emailId, lawyerId)).thenReturn(sampleEmail(null, null));

    mockMvc
        .perform(delete("/api/ai/emails/{emailId}/link", emailId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseId").isEmpty());
  }

  @Test
  void byCaseReturnsLinkedEmails() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID caseId = UUID.randomUUID();
    when(emailLinkingService.findByCase(caseId, lawyerId))
        .thenReturn(List.of(sampleEmail(caseId, null)));

    mockMvc
        .perform(get("/api/ai/cases/{caseId}/emails", caseId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].caseId").value(caseId.toString()));
  }

  @Test
  void byClientReturnsLinkedEmails() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID clientId = UUID.randomUUID();
    when(emailLinkingService.findByClient(clientId, lawyerId))
        .thenReturn(List.of(sampleEmail(null, clientId)));

    mockMvc
        .perform(get("/api/ai/clients/{clientId}/emails", clientId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].clientId").value(clientId.toString()));
  }
}
