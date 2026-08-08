package com.pravoos.ai.practice.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.practice.internal.dto.WorkflowInfo;
import com.pravoos.ai.practice.internal.service.WorkflowService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class WorkflowControllerTest {

  @Mock private WorkflowService workflowService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    WorkflowController controller = new WorkflowController(workflowService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void listReturnsAllWorkflows() throws Exception {
    when(workflowService.listWorkflows())
        .thenReturn(List.of(new WorkflowInfo("BANKRUPTCY_CHECK", "Проверка банкротства", "instr")));

    mockMvc
        .perform(get("/api/ai/workflows"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("BANKRUPTCY_CHECK"))
        .andExpect(jsonPath("$[0].displayName").value("Проверка банкротства"));
  }

  @Test
  void listReturnsEmptyList() throws Exception {
    when(workflowService.listWorkflows()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/ai/workflows"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
