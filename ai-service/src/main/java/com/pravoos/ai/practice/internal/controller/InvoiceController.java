package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CreateInvoiceRequest;
import com.pravoos.ai.practice.internal.dto.ExportedFile;
import com.pravoos.ai.practice.internal.dto.InvoiceResponse;
import com.pravoos.ai.practice.internal.dto.InvoiceSummary;
import com.pravoos.ai.practice.internal.dto.UpdateInvoiceStatusRequest;
import com.pravoos.ai.practice.internal.service.InvoiceService;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.shared.security.CallerContext;
import com.pravoos.ai.shared.util.PagedResponse;
import com.pravoos.ai.shared.util.SecureFileHeaders;
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
      CallerContext caller) {
    return PagedResponse.of(invoiceService.list(caller.userId(), clientId, page, size));
  }

  @PostMapping
  public ResponseEntity<InvoiceResponse> create(
      @Valid @RequestBody CreateInvoiceRequest request, CallerContext caller) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(invoiceService.create(request, caller.userId()));
  }

  @GetMapping("/{invoiceId}")
  public ResponseEntity<InvoiceResponse> get(@PathVariable UUID invoiceId, CallerContext caller) {
    return ResponseEntity.ok(invoiceService.get(invoiceId, caller.userId()));
  }

  @PatchMapping("/{invoiceId}/status")
  public ResponseEntity<InvoiceResponse> updateStatus(
      @PathVariable UUID invoiceId,
      @Valid @RequestBody UpdateInvoiceStatusRequest request,
      CallerContext caller) {
    return ResponseEntity.ok(
        invoiceService.updateStatus(invoiceId, caller.userId(), request.status()));
  }

  @DeleteMapping("/{invoiceId}")
  public ResponseEntity<Void> delete(@PathVariable UUID invoiceId, Authentication authentication) {
    invoiceService.delete(invoiceId, DeletionActor.of(authentication));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{invoiceId}/export")
  public ResponseEntity<byte[]> export(@PathVariable UUID invoiceId, CallerContext caller) {
    ExportedFile file = invoiceService.exportPdf(invoiceId, caller.userId());

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
