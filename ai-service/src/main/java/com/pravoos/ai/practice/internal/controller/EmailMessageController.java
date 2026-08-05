package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.EmailMessageResponse;
import com.pravoos.ai.practice.internal.dto.LinkEmailRequest;
import com.pravoos.ai.practice.internal.service.EmailLinkingService;
import com.pravoos.ai.shared.util.PagedResponse;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class EmailMessageController {

  private final EmailLinkingService emailLinkingService;

  public EmailMessageController(EmailLinkingService emailLinkingService) {
    this.emailLinkingService = emailLinkingService;
  }

  @GetMapping("/emails/unlinked")
  public ResponseEntity<List<EmailMessageResponse>> unlinked(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    return PagedResponse.of(
        emailLinkingService.findUnlinked(SecurityUtils.currentUserId(authentication), page, size));
  }

  @PostMapping("/emails/{emailId}/link")
  public ResponseEntity<EmailMessageResponse> link(
      @PathVariable UUID emailId,
      @Valid @RequestBody LinkEmailRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        emailLinkingService.link(emailId, request, SecurityUtils.currentUserId(authentication)));
  }

  @DeleteMapping("/emails/{emailId}/link")
  public ResponseEntity<EmailMessageResponse> unlink(
      @PathVariable UUID emailId, Authentication authentication) {
    return ResponseEntity.ok(
        emailLinkingService.unlink(emailId, SecurityUtils.currentUserId(authentication)));
  }

  @GetMapping("/cases/{caseId}/emails")
  public ResponseEntity<List<EmailMessageResponse>> byCase(
      @PathVariable UUID caseId, Authentication authentication) {
    return ResponseEntity.ok(
        emailLinkingService.findByCase(caseId, SecurityUtils.currentUserId(authentication)));
  }

  @GetMapping("/clients/{clientId}/emails")
  public ResponseEntity<List<EmailMessageResponse>> byClient(
      @PathVariable UUID clientId, Authentication authentication) {
    return ResponseEntity.ok(
        emailLinkingService.findByClient(clientId, SecurityUtils.currentUserId(authentication)));
  }
}
