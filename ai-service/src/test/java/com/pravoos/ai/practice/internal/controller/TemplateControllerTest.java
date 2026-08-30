package com.pravoos.ai.practice.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.practice.internal.dto.CaseDraftDto;
import com.pravoos.ai.practice.internal.dto.CreateTemplateRequest;
import com.pravoos.ai.practice.internal.dto.TemplateResponse;
import com.pravoos.ai.practice.internal.dto.UpdateTemplateRequest;
import com.pravoos.ai.practice.internal.service.TemplateService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.exception.TemplateNotFoundException;
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
class TemplateControllerTest {

  @Mock private TemplateService templateService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TemplateController controller = new TemplateController(templateService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void createReturns201() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID templateId = UUID.randomUUID();
    CreateTemplateRequest request = new CreateTemplateRequest("Иск", "content");
    when(templateService.create(request, lawyerId))
        .thenReturn(
            new TemplateResponse(templateId, "Иск", "content", LocalDateTime.now(ZoneOffset.UTC)));

    mockMvc
        .perform(
            post("/api/ai/templates")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(templateId.toString()));
  }

  @Test
  void createReturns400WhenNameBlank() throws Exception {
    CreateTemplateRequest request = new CreateTemplateRequest(" ", "content");

    mockMvc
        .perform(
            post("/api/ai/templates")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReturnsTemplatesForLawyer() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID templateId = UUID.randomUUID();
    when(templateService.findByLawyer(lawyerId))
        .thenReturn(
            List.of(
                new TemplateResponse(
                    templateId, "Иск", "content", LocalDateTime.now(ZoneOffset.UTC))));

    mockMvc
        .perform(get("/api/ai/templates").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(templateId.toString()));
  }

  @Test
  void getReturns404WhenTemplateBelongsToAnotherLawyer() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID templateId = UUID.randomUUID();
    when(templateService.get(templateId, lawyerId))
        .thenThrow(new TemplateNotFoundException(templateId));

    mockMvc
        .perform(get("/api/ai/templates/{templateId}", templateId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateReturnsUpdatedTemplate() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID templateId = UUID.randomUUID();
    UpdateTemplateRequest request = new UpdateTemplateRequest("Обновлено", "content");
    when(templateService.update(templateId, request, lawyerId))
        .thenReturn(
            new TemplateResponse(
                templateId, "Обновлено", "content", LocalDateTime.now(ZoneOffset.UTC)));

    mockMvc
        .perform(
            put("/api/ai/templates/{templateId}", templateId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Обновлено"));
  }

  @Test
  void deleteReturns204() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID templateId = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/ai/templates/{templateId}", templateId).principal(authentication))
        .andExpect(status().isNoContent());
  }

  @Test
  void applyToCaseReturns201WithGeneratedDraft() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID caseId = UUID.randomUUID();
    UUID templateId = UUID.randomUUID();
    UUID draftId = UUID.randomUUID();
    when(templateService.applyToCase(caseId, templateId, lawyerId, List.of()))
        .thenReturn(
            new CaseDraftDto(
                draftId,
                caseId,
                "TEMPLATE",
                "Шаблон",
                "Иск",
                "content",
                LocalDateTime.now(ZoneOffset.UTC),
                LocalDateTime.now(ZoneOffset.UTC)));

    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/templates/{templateId}/apply", caseId, templateId)
                .principal(authentication))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(draftId.toString()));
  }
}
