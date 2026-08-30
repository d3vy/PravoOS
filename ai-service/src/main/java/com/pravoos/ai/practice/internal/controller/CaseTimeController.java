package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CaseTimeSummary;
import com.pravoos.ai.practice.internal.dto.CreateTimeEntryRequest;
import com.pravoos.ai.practice.internal.dto.StartTimerRequest;
import com.pravoos.ai.practice.internal.dto.TimeEntryResponse;
import com.pravoos.ai.practice.internal.dto.UpdateTimeEntryRequest;
import com.pravoos.ai.practice.internal.service.TimeEntryService;
import com.pravoos.ai.shared.security.CallerContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/cases/{caseId}/time")
public class CaseTimeController {

  private final TimeEntryService timeEntryService;

  public CaseTimeController(TimeEntryService timeEntryService) {
    this.timeEntryService = timeEntryService;
  }

  @GetMapping
  public ResponseEntity<CaseTimeSummary> summary(@PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(timeEntryService.summary(caseId, caller.userId(), caller.orgIds()));
  }

  @PostMapping
  public ResponseEntity<TimeEntryResponse> create(
      @PathVariable UUID caseId,
      @Valid @RequestBody CreateTimeEntryRequest request,
      CallerContext caller) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(timeEntryService.create(caseId, request, caller.userId(), caller.orgIds()));
  }

  @PatchMapping("/{entryId}")
  public ResponseEntity<TimeEntryResponse> update(
      @PathVariable UUID caseId,
      @PathVariable UUID entryId,
      @Valid @RequestBody UpdateTimeEntryRequest request,
      CallerContext caller) {
    return ResponseEntity.ok(
        timeEntryService.update(caseId, entryId, request, caller.userId(), caller.orgIds()));
  }

  @DeleteMapping("/{entryId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID caseId, @PathVariable UUID entryId, CallerContext caller) {
    timeEntryService.delete(caseId, entryId, caller.userId(), caller.orgIds());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/timer/start")
  public ResponseEntity<TimeEntryResponse> startTimer(
      @PathVariable UUID caseId,
      @Valid @RequestBody StartTimerRequest request,
      CallerContext caller) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(timeEntryService.startTimer(caseId, request, caller.userId(), caller.orgIds()));
  }

  @PostMapping("/timer/stop")
  public ResponseEntity<TimeEntryResponse> stopTimer(
      @PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(timeEntryService.stopTimer(caseId, caller.userId(), caller.orgIds()));
  }
}
