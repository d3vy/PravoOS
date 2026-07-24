package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.TimeEntryResponse;
import com.pravoos.ai.practice.internal.service.TimeEntryService;
import com.pravoos.common.web.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/time")
public class TimeTrackerController {

  private final TimeEntryService timeEntryService;

  public TimeTrackerController(TimeEntryService timeEntryService) {
    this.timeEntryService = timeEntryService;
  }

  @GetMapping("/active")
  public ResponseEntity<TimeEntryResponse> activeTimer(Authentication authentication) {
    return timeEntryService
        .activeTimer(SecurityUtils.currentUserId(authentication))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }
}
