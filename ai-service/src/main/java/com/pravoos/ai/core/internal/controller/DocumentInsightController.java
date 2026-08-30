package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.internal.dto.DocumentInsightResponse;
import com.pravoos.ai.core.internal.service.DocumentInsightService;
import com.pravoos.ai.shared.security.CallerContext;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/document-insights")
public class DocumentInsightController {

  private final DocumentInsightService documentInsightService;

  public DocumentInsightController(DocumentInsightService documentInsightService) {
    this.documentInsightService = documentInsightService;
  }

  @GetMapping("/{documentId}")
  public ResponseEntity<DocumentInsightResponse> summary(
      @PathVariable UUID documentId, CallerContext caller) {
    return ResponseEntity.ok(
        documentInsightService.summary(documentId, caller.userId(), caller.orgIds()));
  }

  @PostMapping("/{documentId}/regenerate")
  public ResponseEntity<DocumentInsightResponse> regenerate(
      @PathVariable UUID documentId, CallerContext caller) {
    return ResponseEntity.ok(
        documentInsightService.regenerate(documentId, caller.userId(), caller.orgIds()));
  }
}
