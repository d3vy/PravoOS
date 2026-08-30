package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.InvoicePaymentResponse;
import com.pravoos.ai.practice.internal.dto.InvoiceResponse;
import com.pravoos.ai.practice.internal.dto.InvoiceSummary;
import com.pravoos.ai.practice.internal.service.InvoicePaymentService;
import com.pravoos.ai.practice.internal.service.PortalInvoiceService;
import com.pravoos.ai.shared.security.CallerContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/portal/invoices")
public class PortalInvoiceController {

  private final PortalInvoiceService portalInvoiceService;
  private final InvoicePaymentService invoicePaymentService;

  public PortalInvoiceController(
      PortalInvoiceService portalInvoiceService, InvoicePaymentService invoicePaymentService) {
    this.portalInvoiceService = portalInvoiceService;
    this.invoicePaymentService = invoicePaymentService;
  }

  @GetMapping
  public ResponseEntity<List<InvoiceSummary>> list(CallerContext caller) {
    return ResponseEntity.ok(portalInvoiceService.listInvoices(caller.clientIds()));
  }

  @GetMapping("/{invoiceId}")
  public ResponseEntity<InvoiceResponse> get(@PathVariable UUID invoiceId, CallerContext caller) {
    return ResponseEntity.ok(portalInvoiceService.getInvoice(invoiceId, caller.clientIds()));
  }

  @PostMapping("/{invoiceId}/pay")
  public ResponseEntity<InvoicePaymentResponse> pay(
      @PathVariable UUID invoiceId, CallerContext caller) {
    return ResponseEntity.ok(invoicePaymentService.createPayment(invoiceId, caller.clientIds()));
  }
}
