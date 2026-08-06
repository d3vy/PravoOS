package com.pravoos.user.push.internal.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.push.internal.config.VapidProperties;
import com.pravoos.user.push.internal.dto.RegisterPushSubscriptionRequest;
import com.pravoos.user.push.internal.dto.UnregisterPushSubscriptionRequest;
import com.pravoos.user.push.internal.service.PushSubscriptionService;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PushControllerTest {

  @Mock private PushSubscriptionService pushSubscriptionService;
  @Mock private Authentication authentication;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID userId = UUID.randomUUID();

  private MockMvc mockMvc(VapidProperties vapidProperties) {
    PushController controller = new PushController(pushSubscriptionService, vapidProperties);
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void configReturnsDisabledWhenPublicKeyMissing() throws Exception {
    mockMvc(new VapidProperties(null))
        .perform(get("/api/user/push/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(false))
        .andExpect(jsonPath("$.publicKey").doesNotExist());
  }

  @Test
  void configReturnsPublicKeyWhenConfigured() throws Exception {
    mockMvc(new VapidProperties("public-key"))
        .perform(get("/api/user/push/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(true))
        .andExpect(jsonPath("$.publicKey").value("public-key"));
  }

  @Test
  void subscribeReturns204AndDelegatesToService() throws Exception {
    org.mockito.Mockito.when(authentication.getPrincipal()).thenReturn(userId.toString());
    RegisterPushSubscriptionRequest request =
        new RegisterPushSubscriptionRequest(
            "https://push.example.com/x", "p256dh-key", "auth-key", "Mozilla/5.0");

    mockMvc(new VapidProperties("public-key"))
        .perform(
            post("/api/user/push/subscriptions")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());

    verify(pushSubscriptionService).register(userId, request);
  }

  @Test
  void subscribeReturns400ForNonHttpsEndpoint() throws Exception {
    RegisterPushSubscriptionRequest request =
        new RegisterPushSubscriptionRequest(
            "http://push.example.com/x", "p256dh-key", "auth-key", null);

    mockMvc(new VapidProperties("public-key"))
        .perform(
            post("/api/user/push/subscriptions")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unsubscribeReturns204AndDelegatesToService() throws Exception {
    org.mockito.Mockito.when(authentication.getPrincipal()).thenReturn(userId.toString());

    mockMvc(new VapidProperties("public-key"))
        .perform(
            post("/api/user/push/subscriptions/remove")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UnregisterPushSubscriptionRequest("https://push.example.com/x"))))
        .andExpect(status().isNoContent());

    verify(pushSubscriptionService).unregister(userId, "https://push.example.com/x");
  }

  @Test
  void unsubscribeReturns400ForBlankEndpoint() throws Exception {
    mockMvc(new VapidProperties("public-key"))
        .perform(
            post("/api/user/push/subscriptions/remove")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new UnregisterPushSubscriptionRequest(""))))
        .andExpect(status().isBadRequest());
  }
}
