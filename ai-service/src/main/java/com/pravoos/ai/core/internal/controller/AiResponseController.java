package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.internal.dto.RateRequest;
import com.pravoos.ai.core.internal.service.AiResponseService;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai/responses")
public class AiResponseController {

    private final AiResponseService aiResponseService;

    public AiResponseController(AiResponseService aiResponseService) {
        this.aiResponseService = aiResponseService;
    }

    @PostMapping("/{responseId}/rate")
    public ResponseEntity<AiResponseDto> rate(@PathVariable UUID responseId,
                                              @Valid @RequestBody RateRequest request,
                                              Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(aiResponseService.rate(responseId, request, lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
    }
}
