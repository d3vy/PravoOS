package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.internal.dto.CheckCitationsRequest;
import com.pravoos.ai.core.internal.dto.CitationCheckResult;
import com.pravoos.ai.core.internal.service.CitationCheckService;
import com.pravoos.ai.shared.security.CallerContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/citation-checks")
public class CitationCheckController {

  private final CitationCheckService citationCheckService;

  public CitationCheckController(CitationCheckService citationCheckService) {
    this.citationCheckService = citationCheckService;
  }

  @PostMapping
  public ResponseEntity<CitationCheckResult> check(
      @Valid @RequestBody CheckCitationsRequest request, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(citationCheckService.check(request.text(), lawyerId));
  }

  @PostMapping("/responses/{responseId}")
  public ResponseEntity<CitationCheckResult> checkResponse(
      @PathVariable UUID responseId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(citationCheckService.checkResponse(responseId, lawyerId));
  }
}
