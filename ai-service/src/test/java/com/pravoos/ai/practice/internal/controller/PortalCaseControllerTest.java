package com.pravoos.ai.practice.internal.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.practice.internal.dto.PortalCaseDetailResponse;
import com.pravoos.ai.practice.internal.dto.PortalCaseResponse;
import com.pravoos.ai.practice.internal.service.PortalCaseService;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.CaseStatus;
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
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PortalCaseControllerTest {

  @Mock private PortalCaseService portalCaseService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final UUID clientId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    PortalCaseController controller = new PortalCaseController(portalCaseService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
  }

  @Test
  void listReturnsCasesVisibleToClient() throws Exception {
    UUID caseId = UUID.randomUUID();
    when(portalCaseService.findCases(List.of(clientId)))
        .thenReturn(
            List.of(
                new PortalCaseResponse(
                    caseId,
                    "Case title",
                    "Description",
                    CaseStatus.IN_PROGRESS,
                    "In progress",
                    null,
                    null,
                    LocalDateTime.now(ZoneOffset.UTC))));

    mockMvc
        .perform(get("/api/ai/portal/cases").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(caseId.toString()));
  }

  @Test
  void listReturnsEmptyWhenNoClientContext() throws Exception {
    when(authentication.getDetails()).thenReturn(null);
    when(portalCaseService.findCases(List.of())).thenReturn(List.of());

    mockMvc
        .perform(get("/api/ai/portal/cases").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void getReturnsCaseDetail() throws Exception {
    UUID caseId = UUID.randomUUID();
    when(portalCaseService.getCase(caseId, List.of(clientId)))
        .thenReturn(
            new PortalCaseDetailResponse(
                caseId,
                "Case title",
                "Description",
                CaseStatus.IN_PROGRESS,
                "In progress",
                null,
                null,
                null,
                "In progress",
                null,
                null,
                LocalDateTime.now(ZoneOffset.UTC),
                List.of()));

    mockMvc
        .perform(get("/api/ai/portal/cases/{caseId}", caseId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Case title"));
  }

  @Test
  void getReturns404WhenCaseNotVisibleToClient() throws Exception {
    UUID caseId = UUID.randomUUID();
    when(portalCaseService.getCase(caseId, List.of(clientId)))
        .thenThrow(new CaseNotFoundException(caseId));

    mockMvc
        .perform(get("/api/ai/portal/cases/{caseId}", caseId).principal(authentication))
        .andExpect(status().isNotFound());
  }
}
