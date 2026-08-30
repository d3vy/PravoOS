package com.pravoos.ai.practice.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.practice.internal.service.InvoicePaymentService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.security.CallerContextArgumentResolver;
import com.pravoos.ai.shared.security.WebhookIpAllowlist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InvoicePaymentWebhookControllerTest {

  @Mock private InvoicePaymentService invoicePaymentService;
  @Mock private WebhookIpAllowlist webhookIpAllowlist;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    InvoicePaymentWebhookController controller =
        new InvoicePaymentWebhookController(invoicePaymentService, webhookIpAllowlist);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void handleWebhook_returns403WhenIpNotAllowed() throws Exception {
    when(webhookIpAllowlist.permits(any())).thenReturn(false);

    mockMvc
        .perform(
            post("/api/ai/billing/invoice-webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"payment.succeeded\",\"object\":{\"id\":\"yk-1\"}}"))
        .andExpect(status().isForbidden());

    verify(invoicePaymentService, never()).handleWebhook(any());
  }

  @Test
  void handleWebhook_returnsOkAndIgnoresNotificationWithoutPaymentId() throws Exception {
    when(webhookIpAllowlist.permits(any())).thenReturn(true);

    mockMvc
        .perform(
            post("/api/ai/billing/invoice-webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"payment.succeeded\",\"object\":{}}"))
        .andExpect(status().isOk());

    verify(invoicePaymentService, never()).handleWebhook(any());
  }

  @Test
  void handleWebhook_delegatesToServiceWhenAllowedAndPaymentIdPresent() throws Exception {
    when(webhookIpAllowlist.permits(any())).thenReturn(true);

    mockMvc
        .perform(
            post("/api/ai/billing/invoice-webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"payment.succeeded\",\"object\":{\"id\":\"yk-123\"}}"))
        .andExpect(status().isOk());

    verify(invoicePaymentService).handleWebhook("yk-123");
  }
}
