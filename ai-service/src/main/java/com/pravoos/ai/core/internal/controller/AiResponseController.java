package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.internal.dto.RateRequest;
import com.pravoos.ai.core.internal.service.AiResponseService;
import com.pravoos.ai.shared.security.CallerContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/responses")
public class AiResponseController {

  private final AiResponseService aiResponseService;

  public AiResponseController(AiResponseService aiResponseService) {
    this.aiResponseService = aiResponseService;
  }

  @PostMapping("/{responseId}/rate")
  public ResponseEntity<AiResponseDto> rate(
      @PathVariable UUID responseId,
      @Valid @RequestBody RateRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(
        aiResponseService.rate(responseId, request, lawyerId, caller.orgIds()));
  }
}
