package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CreateMailboxRequest;
import com.pravoos.ai.practice.internal.dto.MailHostPresetResponse;
import com.pravoos.ai.practice.internal.dto.MailSyncResult;
import com.pravoos.ai.practice.internal.dto.MailboxResponse;
import com.pravoos.ai.practice.internal.dto.MailboxTestResult;
import com.pravoos.ai.practice.internal.dto.UpdateMailboxRequest;
import com.pravoos.ai.practice.internal.service.MailSyncService;
import com.pravoos.ai.practice.internal.service.MailboxService;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.shared.security.CallerContext;
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
  public ResponseEntity<List<MailboxResponse>> list(CallerContext caller) {
    return ResponseEntity.ok(mailboxService.findByUser(caller.userId()));
  }

  @GetMapping("/{mailboxId}")
  public ResponseEntity<MailboxResponse> get(@PathVariable UUID mailboxId, CallerContext caller) {
    return ResponseEntity.ok(mailboxService.get(mailboxId, caller.userId()));
  }

  @PostMapping
  public ResponseEntity<MailboxResponse> create(
      @Valid @RequestBody CreateMailboxRequest request, CallerContext caller) {
    MailboxResponse mailbox = mailboxService.create(request, caller.userId());
    return ResponseEntity.status(HttpStatus.CREATED).body(mailbox);
  }

  @PutMapping("/{mailboxId}")
  public ResponseEntity<MailboxResponse> update(
      @PathVariable UUID mailboxId,
      @Valid @RequestBody UpdateMailboxRequest request,
      CallerContext caller) {
    return ResponseEntity.ok(mailboxService.update(mailboxId, request, caller.userId()));
  }

  @PostMapping("/{mailboxId}/test")
  public ResponseEntity<MailboxTestResult> test(
      @PathVariable UUID mailboxId, CallerContext caller) {
    return ResponseEntity.ok(mailboxService.testConnection(mailboxId, caller.userId()));
  }

  @PostMapping("/{mailboxId}/sync")
  public ResponseEntity<MailSyncResult> sync(@PathVariable UUID mailboxId, CallerContext caller) {
    return ResponseEntity.ok(mailSyncService.syncMailboxForUser(mailboxId, caller.userId()));
  }

  @DeleteMapping("/{mailboxId}")
  public ResponseEntity<Void> delete(@PathVariable UUID mailboxId, Authentication authentication) {
    mailboxService.delete(mailboxId, DeletionActor.of(authentication));
    return ResponseEntity.noContent().build();
  }
}
