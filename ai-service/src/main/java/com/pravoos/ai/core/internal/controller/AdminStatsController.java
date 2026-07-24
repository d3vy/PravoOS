package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.internal.dto.AiStatsResponse;
import com.pravoos.ai.core.internal.service.AdminStatsService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
