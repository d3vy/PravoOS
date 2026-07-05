package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CaseMessageResponse;
import com.pravoos.ai.practice.internal.dto.SendMessageRequest;
import com.pravoos.ai.practice.internal.service.CaseMessageService;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/portal/cases/{caseId}/messages")
public class PortalMessageController {

    private final CaseMessageService caseMessageService;

    public PortalMessageController(CaseMessageService caseMessageService) {
        this.caseMessageService = caseMessageService;
    }

    @GetMapping
    public ResponseEntity<List<CaseMessageResponse>> list(@PathVariable UUID caseId,
                                                          Authentication authentication) {
        return ResponseEntity.ok(caseMessageService.findClientThread(caseId,
                SecurityUtils.currentClientIds(authentication)));
    }

    @PostMapping
    public ResponseEntity<CaseMessageResponse> send(@PathVariable UUID caseId,
                                                    @Valid @RequestBody SendMessageRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                caseMessageService.postClientMessage(caseId, request.body(),
                        SecurityUtils.currentUserId(authentication),
                        SecurityUtils.currentClientIds(authentication)));
    }
}
