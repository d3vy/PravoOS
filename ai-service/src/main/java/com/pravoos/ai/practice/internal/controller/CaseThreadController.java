package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CaseThreadResponse;
import com.pravoos.ai.practice.internal.service.CaseMessageService;
import com.pravoos.common.web.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/messages")
public class CaseThreadController {

    private final CaseMessageService caseMessageService;

    public CaseThreadController(CaseMessageService caseMessageService) {
        this.caseMessageService = caseMessageService;
    }

    @GetMapping("/threads")
    public ResponseEntity<List<CaseThreadResponse>> threads(Authentication authentication) {
        return ResponseEntity.ok(
                caseMessageService.findLawyerThreads(SecurityUtils.currentUserId(authentication)));
    }
}
