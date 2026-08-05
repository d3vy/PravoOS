package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CreateMailboxRequest;
import com.pravoos.ai.practice.internal.dto.MailHostPresetResponse;
import com.pravoos.ai.practice.internal.dto.MailSyncResult;
import com.pravoos.ai.practice.internal.dto.MailboxResponse;
import com.pravoos.ai.practice.internal.dto.MailboxTestResult;
import com.pravoos.ai.practice.internal.dto.UpdateMailboxRequest;
import com.pravoos.ai.practice.internal.service.MailSyncService;
import com.pravoos.ai.practice.internal.service.MailboxService;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/mailboxes")
public class MailboxController {

  private final MailboxService mailboxService;
  private final MailSyncService mailSyncService;

  public MailboxController(MailboxService mailboxService, MailSyncService mailSyncService) {
    this.mailboxService = mailboxService;
    this.mailSyncService = mailSyncService;
  }

  @GetMapping("/presets")
  public ResponseEntity<List<MailHostPresetResponse>> presets() {
    return ResponseEntity.ok(mailboxService.presets());
  }

  @GetMapping
  public ResponseEntity<List<MailboxResponse>> list(Authentication authentication) {
    return ResponseEntity.ok(
        mailboxService.findByUser(SecurityUtils.currentUserId(authentication)));
  }

  @GetMapping("/{mailboxId}")
  public ResponseEntity<MailboxResponse> get(
      @PathVariable UUID mailboxId, Authentication authentication) {
    return ResponseEntity.ok(
        mailboxService.get(mailboxId, SecurityUtils.currentUserId(authentication)));
  }

  @PostMapping
  public ResponseEntity<MailboxResponse> create(
      @Valid @RequestBody CreateMailboxRequest request, Authentication authentication) {
    MailboxResponse mailbox =
        mailboxService.create(request, SecurityUtils.currentUserId(authentication));
    return ResponseEntity.status(HttpStatus.CREATED).body(mailbox);
  }

  @PutMapping("/{mailboxId}")
  public ResponseEntity<MailboxResponse> update(
      @PathVariable UUID mailboxId,
      @Valid @RequestBody UpdateMailboxRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        mailboxService.update(mailboxId, request, SecurityUtils.currentUserId(authentication)));
  }

  @PostMapping("/{mailboxId}/test")
  public ResponseEntity<MailboxTestResult> test(
      @PathVariable UUID mailboxId, Authentication authentication) {
    return ResponseEntity.ok(
        mailboxService.testConnection(mailboxId, SecurityUtils.currentUserId(authentication)));
  }

  @PostMapping("/{mailboxId}/sync")
  public ResponseEntity<MailSyncResult> sync(
      @PathVariable UUID mailboxId, Authentication authentication) {
    return ResponseEntity.ok(
        mailSyncService.syncMailboxForUser(mailboxId, SecurityUtils.currentUserId(authentication)));
  }

  @DeleteMapping("/{mailboxId}")
  public ResponseEntity<Void> delete(@PathVariable UUID mailboxId, Authentication authentication) {
    mailboxService.delete(mailboxId, SecurityUtils.currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }
}
