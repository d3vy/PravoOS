package com.pravoos.ai.practice.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.practice.internal.dto.SaveWorkflowDefinitionRequest;
import com.pravoos.ai.practice.internal.dto.WorkflowDefinitionDto;
import com.pravoos.ai.practice.internal.dto.WorkflowStepDto;
import com.pravoos.ai.practice.internal.service.WorkflowDefinitionService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.exception.WorkflowDefinitionNotFoundException;
import com.pravoos.ai.shared.model.enums.WorkflowCategory;
import com.pravoos.ai.shared.model.enums.WorkflowStepType;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionControllerTest {

  @Mock private WorkflowDefinitionService definitionService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    WorkflowDefinitionController controller = new WorkflowDefinitionController(definitionService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private WorkflowDefinitionDto sampleDto(UUID id) {
    return new WorkflowDefinitionDto(
        id,
        "Взыскание долга",
        "desc",
        WorkflowCategory.DEBT_COLLECTION,
        "Взыскание задолженности",
        false,
        true,
        List.of(),
        LocalDateTime.now(ZoneOffset.UTC),
        LocalDateTime.now(ZoneOffset.UTC));
  }

  @Test
  void listReturnsVisibleDefinitions() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID id = UUID.randomUUID();
    when(definitionService.listVisible(lawyerId, List.of())).thenReturn(List.of(sampleDto(id)));

    mockMvc
        .perform(get("/api/ai/workflow-definitions").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id.toString()));
  }

  @Test
  void getReturns404WhenNotFound() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID id = UUID.randomUUID();
    when(definitionService.get(id, lawyerId, List.of()))
        .thenThrow(new WorkflowDefinitionNotFoundException(id));

    mockMvc
        .perform(get("/api/ai/workflow-definitions/{id}", id).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void createReturns201() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID id = UUID.randomUUID();
    SaveWorkflowDefinitionRequest request =
        new SaveWorkflowDefinitionRequest(
            "Взыскание долга",
            "desc",
            WorkflowCategory.DEBT_COLLECTION,
            List.of(
                new WorkflowStepDto(
                    WorkflowStepType.AI_ANALYSIS, "Анализ", null, null, null, null)));
    when(definitionService.create(request, lawyerId)).thenReturn(sampleDto(id));

    mockMvc
        .perform(
            post("/api/ai/workflow-definitions")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(id.toString()));
  }

  @Test
  void createReturns400WhenStepsEmpty() throws Exception {
    SaveWorkflowDefinitionRequest request =
        new SaveWorkflowDefinitionRequest(
            "Взыскание долга", "desc", WorkflowCategory.CUSTOM, List.of());

    mockMvc
        .perform(
            post("/api/ai/workflow-definitions")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateReturnsUpdatedDefinition() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID id = UUID.randomUUID();
    SaveWorkflowDefinitionRequest request =
        new SaveWorkflowDefinitionRequest(
            "Обновлено",
            "desc",
            WorkflowCategory.CUSTOM,
            List.of(
                new WorkflowStepDto(
                    WorkflowStepType.GENERATE_TASKS, "Задачи", null, null, null, null)));
    when(definitionService.update(id, request, lawyerId, List.of())).thenReturn(sampleDto(id));

    mockMvc
        .perform(
            put("/api/ai/workflow-definitions/{id}", id)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void deleteReturns204() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/ai/workflow-definitions/{id}", id).principal(authentication))
        .andExpect(status().isNoContent());
  }
}
