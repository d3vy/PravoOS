package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CreateInvoiceRequest;
import com.pravoos.ai.practice.internal.dto.ExportedFile;
import com.pravoos.ai.practice.internal.dto.InvoiceResponse;
import com.pravoos.ai.practice.internal.dto.InvoiceSummary;
import com.pravoos.ai.practice.internal.dto.UpdateInvoiceStatusRequest;
import com.pravoos.ai.practice.internal.service.InvoiceService;
import com.pravoos.ai.shared.util.PagedResponse;
import com.pravoos.ai.shared.util.SecureFileHeaders;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/invoices")
public class InvoiceController {

  private final InvoiceService invoiceService;

  public InvoiceController(InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @GetMapping
  public ResponseEntity<List<InvoiceSummary>> list(
      @RequestParam(required = false) UUID clientId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    return PagedResponse.of(
        invoiceService.list(SecurityUtils.currentUserId(authentication), clientId, page, size));
  }

  @PostMapping
  public ResponseEntity<InvoiceResponse> create(
      @Valid @RequestBody CreateInvoiceRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(invoiceService.create(request, SecurityUtils.currentUserId(authentication)));
  }

  @GetMapping("/{invoiceId}")
  public ResponseEntity<InvoiceResponse> get(
      @PathVariable UUID invoiceId, Authentication authentication) {
    return ResponseEntity.ok(
        invoiceService.get(invoiceId, SecurityUtils.currentUserId(authentication)));
  }

  @PatchMapping("/{invoiceId}/status")
  public ResponseEntity<InvoiceResponse> updateStatus(
      @PathVariable UUID invoiceId,
      @Valid @RequestBody UpdateInvoiceStatusRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        invoiceService.updateStatus(
            invoiceId, SecurityUtils.currentUserId(authentication), request.status()));
  }

  @DeleteMapping("/{invoiceId}")
  public ResponseEntity<Void> delete(@PathVariable UUID invoiceId, Authentication authentication) {
    invoiceService.delete(invoiceId, SecurityUtils.currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{invoiceId}/export")
  public ResponseEntity<byte[]> export(
      @PathVariable UUID invoiceId, Authentication authentication) {
    ExportedFile file =
        invoiceService.exportPdf(invoiceId, SecurityUtils.currentUserId(authentication));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build());
    SecureFileHeaders.apply(headers);

    return ResponseEntity.ok()
        .headers(headers)
        .contentType(MediaType.parseMediaType(file.contentType()))
        .body(file.content());
  }
}
