package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.DashboardResponse;
import com.pravoos.ai.practice.internal.service.DashboardService;
import com.pravoos.ai.shared.security.CallerContext;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/dashboard")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @GetMapping
  public ResponseEntity<DashboardResponse> dashboard(CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(dashboardService.getDashboard(lawyerId));
  }
}
