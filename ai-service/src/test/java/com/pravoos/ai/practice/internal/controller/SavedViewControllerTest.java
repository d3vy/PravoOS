package com.pravoos.ai.practice.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.practice.internal.dto.CreateSavedViewRequest;
import com.pravoos.ai.practice.internal.dto.SavedViewResponse;
import com.pravoos.ai.practice.internal.dto.UpdateSavedViewRequest;
import com.pravoos.ai.practice.internal.model.SavedViewScope;
import com.pravoos.ai.practice.internal.service.SavedViewService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
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
class SavedViewControllerTest {

  @Mock private SavedViewService savedViewService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    SavedViewController controller = new SavedViewController(savedViewService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void listReturnsVisibleViewsForScope() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID viewId = UUID.randomUUID();
    when(savedViewService.findVisible(SavedViewScope.CASES, lawyerId, List.of()))
        .thenReturn(
            List.of(
                new SavedViewResponse(
                    viewId,
                    SavedViewScope.CASES,
                    "Мои дела",
                    "{}",
                    false,
                    null,
                    true,
                    LocalDateTime.now(ZoneOffset.UTC))));

    mockMvc
        .perform(get("/api/ai/saved-views").param("scope", "CASES").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(viewId.toString()));
  }

  @Test
  void listReturns500WhenScopeMissing() throws Exception {
    // GlobalExceptionHandler не мапит MissingServletRequestParameterException — падает в generic
    // Exception-хендлер (500), тот же пробел, что и с отсутствующими заголовком/query-параметром
    // в AuthApplicationControllerTest/PortalAuthControllerTest (user-service). Документируем as-is.
    mockMvc
        .perform(get("/api/ai/saved-views").principal(authentication))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void createReturns201() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID viewId = UUID.randomUUID();
    CreateSavedViewRequest request =
        new CreateSavedViewRequest(SavedViewScope.CASES, "Мои дела", "{}", false, null);
    when(savedViewService.create(request, lawyerId, List.of()))
        .thenReturn(
            new SavedViewResponse(
                viewId,
                SavedViewScope.CASES,
                "Мои дела",
                "{}",
                false,
                null,
                true,
                LocalDateTime.now(ZoneOffset.UTC)));

    mockMvc
        .perform(
            post("/api/ai/saved-views")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(viewId.toString()));
  }

  @Test
  void createReturns400WhenNameBlank() throws Exception {
    CreateSavedViewRequest request =
        new CreateSavedViewRequest(SavedViewScope.CASES, " ", "{}", false, null);

    mockMvc
        .perform(
            post("/api/ai/saved-views")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateReturnsUpdatedView() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID viewId = UUID.randomUUID();
    UpdateSavedViewRequest request = new UpdateSavedViewRequest("Обновлено", "{}", true, null);
    when(savedViewService.update(viewId, request, lawyerId, List.of()))
        .thenReturn(
            new SavedViewResponse(
                viewId,
                SavedViewScope.CASES,
                "Обновлено",
                "{}",
                true,
                null,
                true,
                LocalDateTime.now(ZoneOffset.UTC)));

    mockMvc
        .perform(
            put("/api/ai/saved-views/{viewId}", viewId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Обновлено"));
  }

  @Test
  void deleteReturns204() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID viewId = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/ai/saved-views/{viewId}", viewId).principal(authentication))
        .andExpect(status().isNoContent());
  }
}
