package com.pravoos.user.billing.internal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.pravoos.user.billing.internal.security.WebhookIpAllowlist;
import com.pravoos.user.billing.internal.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing")
public class BillingWebhookController {

  private static final Logger log = LoggerFactory.getLogger(BillingWebhookController.class);

  private final PaymentService paymentService;
  private final WebhookIpAllowlist webhookIpAllowlist;

  public BillingWebhookController(
      PaymentService paymentService, WebhookIpAllowlist webhookIpAllowlist) {
    this.paymentService = paymentService;
    this.webhookIpAllowlist = webhookIpAllowlist;
  }

  @PostMapping("/webhook")
  public ResponseEntity<Void> handleWebhook(
      @RequestBody JsonNode notification, HttpServletRequest request) {
    if (!webhookIpAllowlist.permits(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    String providerPaymentId = notification.path("object").path("id").asText(null);
    if (providerPaymentId == null || providerPaymentId.isBlank()) {
      log.warn(
          "Webhook without payment id ignored: event={}", notification.path("event").asText(""));
      return ResponseEntity.ok().build();
    }

    paymentService.handleNotification(providerPaymentId);
    return ResponseEntity.ok().build();
  }
}
