package com.pravoos.user.identity.internal.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.identity.internal.dto.DigestPreferenceRequest;
import com.pravoos.user.identity.internal.service.UserService;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InternalUserControllerTest {

  @Mock private UserService userService;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    InternalUserController controller = new InternalUserController(userService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void digestPreferencesReturnsFilteredUserIds() throws Exception {
    UUID enabled = UUID.randomUUID();
    UUID disabled = UUID.randomUUID();
    when(userService.filterDigestEnabled(anyList())).thenReturn(List.of(enabled));

    mockMvc
        .perform(
            post("/internal/users/digest-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new DigestPreferenceRequest(List.of(enabled, disabled)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userIds.length()").value(1))
        .andExpect(jsonPath("$.userIds[0]").value(enabled.toString()));
  }

  @Test
  void digestPreferencesReturnsEmptyListWhenNoneEnabled() throws Exception {
    when(userService.filterDigestEnabled(anyList())).thenReturn(List.of());

    mockMvc
        .perform(
            post("/internal/users/digest-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new DigestPreferenceRequest(List.of(UUID.randomUUID())))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userIds.length()").value(0));
  }

  @Test
  void digestPreferencesReturns400WhenUserIdsMissing() throws Exception {
    mockMvc
        .perform(
            post("/internal/users/digest-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }
}
