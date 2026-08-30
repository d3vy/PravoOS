package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CaseThreadResponse;
import com.pravoos.ai.practice.internal.service.CaseMessageService;
import com.pravoos.ai.shared.security.CallerContext;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/messages")
public class CaseThreadController {

  private final CaseMessageService caseMessageService;

  public CaseThreadController(CaseMessageService caseMessageService) {
    this.caseMessageService = caseMessageService;
  }

  @GetMapping("/threads")
  public ResponseEntity<List<CaseThreadResponse>> threads(CallerContext caller) {
    return ResponseEntity.ok(caseMessageService.findLawyerThreads(caller.userId()));
  }
}
