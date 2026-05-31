package com.pravoos.ai.controller;

import com.pravoos.ai.model.dto.AiResponseDto;
import com.pravoos.ai.model.dto.AiStatsResponse;
import com.pravoos.ai.service.AdminStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/admin")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/ai-stats")
    public ResponseEntity<AiStatsResponse> stats() {
        return ResponseEntity.ok(adminStatsService.getStats());
    }

    @GetMapping("/ai-responses")
    public ResponseEntity<List<AiResponseDto>> recentResponses() {
        return ResponseEntity.ok(adminStatsService.getRecentResponses());
    }
}
