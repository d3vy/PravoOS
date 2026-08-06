package com.pravoos.user.push.internal.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.push.internal.dto.PushSubscriptionView;
import com.pravoos.user.push.internal.dto.UnregisterPushSubscriptionRequest;
import com.pravoos.user.push.internal.service.PushSubscriptionService;
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
class InternalPushControllerTest {

  @Mock private PushSubscriptionService pushSubscriptionService;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    InternalPushController controller = new InternalPushController(pushSubscriptionService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void subscriptionsReturnsListForUser() throws Exception {
    UUID userId = UUID.randomUUID();
    when(pushSubscriptionService.subscriptionsOf(userId))
        .thenReturn(
            List.of(new PushSubscriptionView("https://push.example.com/x", "p256dh", "auth")));

    mockMvc
        .perform(get("/internal/push/subscriptions/{userId}", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].endpoint").value("https://push.example.com/x"));
  }

  @Test
  void subscriptionsReturnsEmptyListWhenNoneRegistered() throws Exception {
    UUID userId = UUID.randomUUID();
    when(pushSubscriptionService.subscriptionsOf(userId)).thenReturn(List.of());

    mockMvc
        .perform(get("/internal/push/subscriptions/{userId}", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void pruneReturns204AndDelegatesToService() throws Exception {
    mockMvc
        .perform(
            post("/internal/push/subscriptions/prune")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UnregisterPushSubscriptionRequest("https://push.example.com/x"))))
        .andExpect(status().isNoContent());

    verify(pushSubscriptionService).prune("https://push.example.com/x");
  }

  @Test
  void pruneReturns400ForBlankEndpoint() throws Exception {
    mockMvc
        .perform(
            post("/internal/push/subscriptions/prune")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new UnregisterPushSubscriptionRequest(""))))
        .andExpect(status().isBadRequest());
  }
}
