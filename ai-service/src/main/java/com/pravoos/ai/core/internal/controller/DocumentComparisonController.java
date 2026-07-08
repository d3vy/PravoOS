package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.internal.dto.CreateComparisonRequest;
import com.pravoos.ai.core.internal.dto.DocumentComparisonDto;
import com.pravoos.ai.core.internal.service.DocumentComparisonService;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/document-comparisons")
public class DocumentComparisonController {

    private final DocumentComparisonService documentComparisonService;

    public DocumentComparisonController(DocumentComparisonService documentComparisonService) {
        this.documentComparisonService = documentComparisonService;
    }

    @PostMapping
    public ResponseEntity<DocumentComparisonDto> create(@Valid @RequestBody CreateComparisonRequest request,
                                                        Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentComparisonService.compare(request.baseDocumentId(), request.revisedDocumentId(),
                        lawyerId, SecurityUtils.currentOrgIds(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<DocumentComparisonDto>> listByCase(@RequestParam UUID caseId,
                                                                 Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(documentComparisonService.findByCase(caseId, lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
    }

    @GetMapping("/{comparisonId}")
    public ResponseEntity<DocumentComparisonDto> get(@PathVariable UUID comparisonId, Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(documentComparisonService.get(comparisonId, lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
    }
}
