package com.pravoos.ai.practice.internal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.pravoos.ai.practice.internal.service.InvoicePaymentService;
import com.pravoos.ai.shared.security.WebhookIpAllowlist;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/billing")
public class InvoicePaymentWebhookController {

  private static final Logger log = LoggerFactory.getLogger(InvoicePaymentWebhookController.class);

  private final InvoicePaymentService invoicePaymentService;
  private final WebhookIpAllowlist webhookIpAllowlist;

  public InvoicePaymentWebhookController(
      InvoicePaymentService invoicePaymentService, WebhookIpAllowlist webhookIpAllowlist) {
    this.invoicePaymentService = invoicePaymentService;
    this.webhookIpAllowlist = webhookIpAllowlist;
  }

  @PostMapping("/invoice-webhook")
  public ResponseEntity<Void> handleWebhook(
      @RequestBody JsonNode payload, HttpServletRequest request) {
    if (!webhookIpAllowlist.permits(request)) {
      return ResponseEntity.status(403).build();
    }
    String providerPaymentId = payload.path("object").path("id").asText(null);
    if (providerPaymentId == null) {
      log.warn("Ignoring YooKassa webhook without payment id");
      return ResponseEntity.ok().build();
    }
    invoicePaymentService.handleWebhook(providerPaymentId);
    return ResponseEntity.ok().build();
  }
}
