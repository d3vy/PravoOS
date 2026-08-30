package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CaseMessageResponse;
import com.pravoos.ai.practice.internal.dto.SendMessageRequest;
import com.pravoos.ai.practice.internal.service.CaseMessageService;
import com.pravoos.ai.shared.security.CallerContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/cases/{caseId}/messages")
public class CaseMessageController {

  private final CaseMessageService caseMessageService;

  public CaseMessageController(CaseMessageService caseMessageService) {
    this.caseMessageService = caseMessageService;
  }

  @GetMapping
  public ResponseEntity<List<CaseMessageResponse>> list(
      @PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(
        caseMessageService.findLawyerThread(caseId, caller.userId(), caller.orgIds()));
  }

  @PostMapping("/read")
  public ResponseEntity<Void> markRead(@PathVariable UUID caseId, CallerContext caller) {
    caseMessageService.markThreadRead(caseId, caller.userId(), caller.orgIds());
    return ResponseEntity.noContent().build();
  }

  @PostMapping
  public ResponseEntity<CaseMessageResponse> send(
      @PathVariable UUID caseId,
      @Valid @RequestBody SendMessageRequest request,
      CallerContext caller) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            caseMessageService.postLawyerMessage(
                caseId, request.body(), caller.userId(), caller.orgIds()));
  }
}
