package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.EmailAttachmentImportResult;
import com.pravoos.ai.practice.internal.dto.EmailAttachmentResponse;
import com.pravoos.ai.practice.internal.service.EmailAttachmentImportService;
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
@RequestMapping("/api/ai/emails")
public class EmailAttachmentController {

  private final EmailAttachmentImportService emailAttachmentImportService;

  public EmailAttachmentController(EmailAttachmentImportService emailAttachmentImportService) {
    this.emailAttachmentImportService = emailAttachmentImportService;
  }

  @GetMapping("/{emailId}/attachments")
  public ResponseEntity<List<EmailAttachmentResponse>> attachments(
      @PathVariable UUID emailId, CallerContext caller) {
    return ResponseEntity.ok(emailAttachmentImportService.findByEmail(emailId, caller.userId()));
  }

  @PostMapping("/{emailId}/attachments/import")
  public ResponseEntity<EmailAttachmentImportResult> importAttachments(
      @PathVariable UUID emailId, CallerContext caller) {
    return ResponseEntity.ok(
        emailAttachmentImportService.importAttachments(emailId, caller.userId()));
  }
}
