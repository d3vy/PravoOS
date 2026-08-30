package com.pravoos.ai.practice.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.practice.internal.dto.CaseThreadResponse;
import com.pravoos.ai.practice.internal.service.CaseMessageService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.MessageAuthorRole;
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
class CaseThreadControllerTest {

  @Mock private CaseMessageService caseMessageService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    CaseThreadController controller = new CaseThreadController(caseMessageService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
  }

  @Test
  void threadsReturnsLawyerThreads() throws Exception {
    UUID caseId = UUID.randomUUID();
    when(caseMessageService.findLawyerThreads(lawyerId))
        .thenReturn(
            List.of(
                new CaseThreadResponse(
                    caseId,
                    "Дело №1",
                    "Иванов И.И.",
                    "последнее сообщение",
                    MessageAuthorRole.CLIENT,
                    LocalDateTime.now(ZoneOffset.UTC),
                    3)));

    mockMvc
        .perform(get("/api/ai/messages/threads").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].caseId").value(caseId.toString()))
        .andExpect(jsonPath("$[0].unreadCount").value(3));
  }

  @Test
  void threadsReturnsEmptyListWhenNoThreads() throws Exception {
    when(caseMessageService.findLawyerThreads(lawyerId)).thenReturn(List.of());

    mockMvc
        .perform(get("/api/ai/messages/threads").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
