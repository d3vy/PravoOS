package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.model.dto.PortalCaseDetailResponse;
import com.pravoos.ai.model.dto.PortalCaseResponse;
import com.pravoos.ai.practice.internal.service.PortalCaseService;
import com.pravoos.common.web.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/portal/cases")
public class PortalCaseController {

    private final PortalCaseService portalCaseService;

    public PortalCaseController(PortalCaseService portalCaseService) {
        this.portalCaseService = portalCaseService;
    }

    @GetMapping
    public ResponseEntity<List<PortalCaseResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(
                portalCaseService.findCases(SecurityUtils.currentClientIds(authentication)));
    }

    @GetMapping("/{caseId}")
    public ResponseEntity<PortalCaseDetailResponse> get(@PathVariable UUID caseId,
                                                        Authentication authentication) {
        return ResponseEntity.ok(
                portalCaseService.getCase(caseId, SecurityUtils.currentClientIds(authentication)));
    }
}
