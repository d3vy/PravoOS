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
@RequestMapping("/api/ai/portal/cases/{caseId}/messages")
public class PortalMessageController {

  private final CaseMessageService caseMessageService;

  public PortalMessageController(CaseMessageService caseMessageService) {
    this.caseMessageService = caseMessageService;
  }

  @GetMapping
  public ResponseEntity<List<CaseMessageResponse>> list(
      @PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(caseMessageService.findClientThread(caseId, caller.clientIds()));
  }

  @PostMapping
  public ResponseEntity<CaseMessageResponse> send(
      @PathVariable UUID caseId,
      @Valid @RequestBody SendMessageRequest request,
      CallerContext caller) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            caseMessageService.postClientMessage(
                caseId, request.body(), caller.userId(), caller.clientIds()));
  }
}
