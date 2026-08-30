package com.pravoos.ai.practice.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiResponseQuery;
import com.pravoos.ai.practice.internal.dto.CaseResponse;
import com.pravoos.ai.practice.internal.dto.CaseTaskResponse;
import com.pravoos.ai.practice.internal.dto.CreateCaseRequest;
import com.pravoos.ai.practice.internal.dto.CreateCaseTaskRequest;
import com.pravoos.ai.practice.internal.dto.UpdateCaseRequest;
import com.pravoos.ai.practice.internal.dto.UpdateCaseStatusRequest;
import com.pravoos.ai.practice.internal.service.CaseAnalyticsService;
import com.pravoos.ai.practice.internal.service.CaseExportService;
import com.pravoos.ai.practice.internal.service.CaseService;
import com.pravoos.ai.practice.internal.service.CaseTaskService;
import com.pravoos.ai.practice.internal.service.WorkflowExecutionService;
import com.pravoos.ai.practice.internal.service.WorkflowService;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.CourtSystem;
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
class CaseControllerTest {

  @Mock private CaseService caseService;
  @Mock private WorkflowService workflowService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private AiResponseQuery aiResponseQuery;
  @Mock private CaseExportService caseExportService;
  @Mock private CaseTaskService caseTaskService;
  @Mock private CaseAnalyticsService caseAnalyticsService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    CaseController controller =
        new CaseController(
            caseService,
            workflowService,
            workflowExecutionService,
            aiResponseQuery,
            caseExportService,
            caseTaskService,
            caseAnalyticsService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private CaseResponse sampleResponse() {
    return new CaseResponse(
        caseId,
        lawyerId,
        null,
        "Дело №1",
        "Описание",
        null,
        null,
        CaseStatus.IN_PROGRESS,
        CaseStatus.IN_PROGRESS.getDisplayName(),
        null,
        null,
        null,
        CourtSystem.GENERAL_JURISDICTION,
        CourtSystem.GENERAL_JURISDICTION.getDisplayName(),
        null,
        null,
        null,
        LocalDateTime.now(ZoneOffset.UTC));
  }

  @Test
  void createReturnsCreatedCase() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(caseService.create(any(CreateCaseRequest.class), eq(lawyerId), anyList()))
        .thenReturn(sampleResponse());

    mockMvc
        .perform(
            post("/api/ai/cases")
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new CreateCaseRequest(
                            "Дело №1", null, null, null, null, null, null, null, null, null))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Дело №1"));
  }

  @Test
  void createRejectsBlankTitle() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/cases")
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new CreateCaseRequest(
                            "", null, null, null, null, null, null, null, null, null))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(caseService);
  }

  @Test
  void getReturnsCaseWhenVisible() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(caseService.get(eq(caseId), eq(lawyerId), anyList())).thenReturn(sampleResponse());

    mockMvc
        .perform(get("/api/ai/cases/{caseId}", caseId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(caseId.toString()));
  }

  @Test
  void getReturnsNotFoundForUnknownCase() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(caseService.get(eq(caseId), eq(lawyerId), anyList()))
        .thenThrow(new CaseNotFoundException(caseId));

    mockMvc
        .perform(get("/api/ai/cases/{caseId}", caseId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateReturnsUpdatedCase() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(caseService.update(eq(caseId), any(UpdateCaseRequest.class), eq(lawyerId), anyList()))
        .thenReturn(sampleResponse());

    mockMvc
        .perform(
            patch("/api/ai/cases/{caseId}", caseId)
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateCaseRequest(
                            "Дело №1", null, null, null, null, null, null, null, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Дело №1"));
  }

  @Test
  void updateStatusDelegatesToService() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(caseService.updateStatus(eq(caseId), eq(CaseStatus.CLOSED_WON), eq(lawyerId), anyList()))
        .thenReturn(sampleResponse());

    mockMvc
        .perform(
            patch("/api/ai/cases/{caseId}/status", caseId)
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateCaseStatusRequest(CaseStatus.CLOSED_WON))))
        .andExpect(status().isOk());
  }

  @Test
  void updateStatusRejectsNullStatus() throws Exception {
    mockMvc
        .perform(
            patch("/api/ai/cases/{caseId}/status", caseId)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(caseService);
  }

  @Test
  void deleteRemovesCaseAndReturnsNoContent() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());

    mockMvc
        .perform(delete("/api/ai/cases/{caseId}", caseId).principal(authentication))
        .andExpect(status().isNoContent());
    verify(caseService).delete(eq(caseId), any(DeletionActor.class));
  }

  @Test
  void tasksReturnsTaskList() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(caseTaskService.findByCase(eq(caseId), eq(lawyerId), anyList()))
        .thenReturn(
            List.of(
                new CaseTaskResponse(
                    UUID.randomUUID(),
                    caseId,
                    "Подготовить документы",
                    null,
                    false,
                    LocalDateTime.now(ZoneOffset.UTC))));

    mockMvc
        .perform(get("/api/ai/cases/{caseId}/tasks", caseId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].text").value("Подготовить документы"));
  }

  @Test
  void createTaskReturnsCreatedTask() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID taskId = UUID.randomUUID();
    when(caseTaskService.create(
            eq(caseId), any(CreateCaseTaskRequest.class), eq(lawyerId), anyList()))
        .thenReturn(
            new CaseTaskResponse(
                taskId, caseId, "Новая задача", null, false, LocalDateTime.now(ZoneOffset.UTC)));

    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/tasks", caseId)
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new CreateCaseTaskRequest("Новая задача", null))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(taskId.toString()));
  }

  @Test
  void createTaskRejectsBlankText() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/tasks", caseId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new CreateCaseTaskRequest("", null))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(caseTaskService);
  }

  @Test
  void deleteTaskReturnsNoContent() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID taskId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/ai/cases/{caseId}/tasks/{taskId}", caseId, taskId)
                .principal(authentication))
        .andExpect(status().isNoContent());
    verify(caseTaskService).delete(eq(caseId), eq(taskId), eq(lawyerId), anyList());
  }
}
