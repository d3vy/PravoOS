package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CreateSignatureRequestDto;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.service.SignatureService;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/cases/{caseId}/signatures")
public class SignatureController {

    private final SignatureService signatureService;

    public SignatureController(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @PostMapping
    public ResponseEntity<SignatureRequestResponse> create(@PathVariable UUID caseId,
                                                           @Valid @RequestBody CreateSignatureRequestDto request,
                                                           Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(signatureService.create(caseId, request, lawyerId));
    }

    @GetMapping
    public ResponseEntity<List<SignatureRequestResponse>> list(@PathVariable UUID caseId,
                                                              Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(signatureService.findByCase(caseId, lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
    }

    @PostMapping("/{signatureId}/cancel")
    public ResponseEntity<SignatureRequestResponse> cancel(@PathVariable UUID caseId,
                                                          @PathVariable UUID signatureId,
                                                          Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(signatureService.cancel(caseId, signatureId, lawyerId));
    }
}
