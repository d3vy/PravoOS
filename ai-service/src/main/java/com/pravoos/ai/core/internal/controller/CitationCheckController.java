package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.model.dto.CheckCitationsRequest;
import com.pravoos.ai.model.dto.CitationCheckResult;
import com.pravoos.ai.core.internal.service.CitationCheckService;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai/citation-checks")
public class CitationCheckController {

    private final CitationCheckService citationCheckService;

    public CitationCheckController(CitationCheckService citationCheckService) {
        this.citationCheckService = citationCheckService;
    }

    @PostMapping
    public ResponseEntity<CitationCheckResult> check(@Valid @RequestBody CheckCitationsRequest request,
                                                     Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(citationCheckService.check(request.text(), lawyerId));
    }

    @PostMapping("/responses/{responseId}")
    public ResponseEntity<CitationCheckResult> checkResponse(@PathVariable UUID responseId,
                                                             Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(citationCheckService.checkResponse(responseId, lawyerId));
    }
}
