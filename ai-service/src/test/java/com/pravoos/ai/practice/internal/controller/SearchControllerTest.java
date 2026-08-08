package com.pravoos.ai.practice.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse;
import com.pravoos.ai.practice.internal.service.SearchService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
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
class SearchControllerTest {

  @Mock private SearchService searchService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    SearchController controller = new SearchController(searchService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
  }

  @Test
  void searchDelegatesQueryAndContentFlag() throws Exception {
    when(searchService.search(lawyerId, "Иванов", false))
        .thenReturn(
            new GlobalSearchResponse(List.of(), List.of(), List.of(), List.of(), List.of()));

    mockMvc
        .perform(
            get("/api/ai/search")
                .param("q", "Иванов")
                .param("content", "false")
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cases.length()").value(0));
  }

  @Test
  void searchDefaultsContentToTrueAndAllowsMissingQuery() throws Exception {
    when(searchService.search(lawyerId, null, true))
        .thenReturn(
            new GlobalSearchResponse(List.of(), List.of(), List.of(), List.of(), List.of()));

    mockMvc.perform(get("/api/ai/search").principal(authentication)).andExpect(status().isOk());
  }
}
