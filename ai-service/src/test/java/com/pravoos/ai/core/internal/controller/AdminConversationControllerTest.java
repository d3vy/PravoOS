package com.pravoos.ai.core.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.core.internal.dto.AdminConversationResponse;
import com.pravoos.ai.core.internal.dto.MessageResponse;
import com.pravoos.ai.core.internal.service.AdminConversationService;
import com.pravoos.ai.shared.exception.ConversationNotFoundException;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.AuditAction;
import com.pravoos.ai.shared.model.enums.MessageRole;
import com.pravoos.ai.shared.service.AccessAuditService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminConversationControllerTest {

  @Mock private AdminConversationService adminConversationService;
  @Mock private AccessAuditService accessAuditService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AdminConversationController(adminConversationService, accessAuditService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void listPassesFiltersAndReportsTotal() throws Exception {
    UUID orgId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    when(adminConversationService.getConversations(
            eq(orgId), eq(lawyerId), eq("иск"), eq(0), eq(20)))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new AdminConversationResponse(
                        "conv-1", lawyerId, orgId, "Иск", null, null, now, now)),
                PageRequest.of(0, 20),
                1));

    mockMvc
        .perform(
            get("/api/ai/admin/conversations")
                .param("orgId", orgId.toString())
                .param("lawyerId", lawyerId.toString())
                .param("q", "иск")
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "1"))
        .andExpect(jsonPath("$[0].id").value("conv-1"));

    verifyNoInteractions(accessAuditService);
  }

  @Test
  void readingMessagesIsAudited() throws Exception {
    when(adminConversationService.getMessages(eq("conv-1"), eq(0), eq(50)))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new MessageResponse(
                        "m1",
                        MessageRole.USER,
                        "Вопрос",
                        List.of(),
                        null,
                        LocalDateTime.now(ZoneOffset.UTC),
                        List.of())),
                PageRequest.of(0, 50),
                1));

    mockMvc
        .perform(
            get("/api/ai/admin/conversations/{id}/messages", "conv-1").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].content").value("Вопрос"));

    verify(accessAuditService)
        .recordRef(eq(authentication), eq(AuditAction.AI_CONVERSATION_VIEW), eq("conv-1"), any());
  }

  @Test
  void missingConversationIsNotAudited() throws Exception {
    when(adminConversationService.getMessages(eq("gone"), eq(0), eq(50)))
        .thenThrow(new ConversationNotFoundException("gone"));

    mockMvc
        .perform(get("/api/ai/admin/conversations/{id}/messages", "gone").principal(authentication))
        .andExpect(status().isNotFound());

    verify(accessAuditService, org.mockito.Mockito.never())
        .recordRef(any(), any(), isNull(), any());
    verifyNoInteractions(accessAuditService);
  }
}
