package com.pravoos.user.billing.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.user.billing.internal.security.WebhookIpAllowlist;
import com.pravoos.user.billing.internal.service.PaymentService;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BillingWebhookControllerTest {

  @Mock private PaymentService paymentService;
  @Mock private WebhookIpAllowlist webhookIpAllowlist;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    BillingWebhookController controller =
        new BillingWebhookController(paymentService, webhookIpAllowlist);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void handleWebhook_returns403WhenIpNotAllowed() throws Exception {
    when(webhookIpAllowlist.permits(any())).thenReturn(false);

    mockMvc
        .perform(
            post("/api/billing/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"payment.succeeded\",\"object\":{\"id\":\"yk-1\"}}"))
        .andExpect(status().isForbidden());

    verify(paymentService, never()).handleNotification(any());
  }

  @Test
  void handleWebhook_returnsOkAndIgnoresNotificationWithoutPaymentId() throws Exception {
    when(webhookIpAllowlist.permits(any())).thenReturn(true);

    mockMvc
        .perform(
            post("/api/billing/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"payment.succeeded\",\"object\":{}}"))
        .andExpect(status().isOk());

    verify(paymentService, never()).handleNotification(any());
  }

  @Test
  void handleWebhook_delegatesToPaymentServiceWhenAllowedAndPaymentIdPresent() throws Exception {
    when(webhookIpAllowlist.permits(any())).thenReturn(true);

    mockMvc
        .perform(
            post("/api/billing/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"payment.succeeded\",\"object\":{\"id\":\"yk-123\"}}"))
        .andExpect(status().isOk());

    verify(paymentService).handleNotification("yk-123");
  }
}
