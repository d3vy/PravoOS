package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.PortalCaseDetailResponse;
import com.pravoos.ai.practice.internal.dto.PortalCaseResponse;
import com.pravoos.ai.practice.internal.service.PortalCaseService;
import com.pravoos.ai.shared.security.CallerContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/portal/cases")
public class PortalCaseController {

  private final PortalCaseService portalCaseService;

  public PortalCaseController(PortalCaseService portalCaseService) {
    this.portalCaseService = portalCaseService;
  }

  @GetMapping
  public ResponseEntity<List<PortalCaseResponse>> list(CallerContext caller) {
    return ResponseEntity.ok(portalCaseService.findCases(caller.clientIds()));
  }

  @GetMapping("/{caseId}")
  public ResponseEntity<PortalCaseDetailResponse> get(
      @PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(portalCaseService.getCase(caseId, caller.clientIds()));
  }
}
