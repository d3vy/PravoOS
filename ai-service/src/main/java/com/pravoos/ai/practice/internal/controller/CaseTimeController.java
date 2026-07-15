package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CaseTimeSummary;
import com.pravoos.ai.practice.internal.dto.CreateTimeEntryRequest;
import com.pravoos.ai.practice.internal.dto.StartTimerRequest;
import com.pravoos.ai.practice.internal.dto.TimeEntryResponse;
import com.pravoos.ai.practice.internal.dto.UpdateTimeEntryRequest;
import com.pravoos.ai.practice.internal.service.TimeEntryService;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai/cases/{caseId}/time")
public class CaseTimeController {

    private final TimeEntryService timeEntryService;

    public CaseTimeController(TimeEntryService timeEntryService) {
        this.timeEntryService = timeEntryService;
    }

    @GetMapping
    public ResponseEntity<CaseTimeSummary> summary(@PathVariable UUID caseId, Authentication authentication) {
        return ResponseEntity.ok(timeEntryService.summary(caseId,
                SecurityUtils.currentUserId(authentication), SecurityUtils.currentOrgIds(authentication)));
    }

    @PostMapping
    public ResponseEntity<TimeEntryResponse> create(@PathVariable UUID caseId,
                                                    @Valid @RequestBody CreateTimeEntryRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(timeEntryService.create(caseId, request,
                SecurityUtils.currentUserId(authentication), SecurityUtils.currentOrgIds(authentication)));
    }

    @PatchMapping("/{entryId}")
    public ResponseEntity<TimeEntryResponse> update(@PathVariable UUID caseId,
                                                    @PathVariable UUID entryId,
                                                    @Valid @RequestBody UpdateTimeEntryRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.ok(timeEntryService.update(caseId, entryId, request,
                SecurityUtils.currentUserId(authentication), SecurityUtils.currentOrgIds(authentication)));
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> delete(@PathVariable UUID caseId,
                                       @PathVariable UUID entryId,
                                       Authentication authentication) {
        timeEntryService.delete(caseId, entryId,
                SecurityUtils.currentUserId(authentication), SecurityUtils.currentOrgIds(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/timer/start")
    public ResponseEntity<TimeEntryResponse> startTimer(@PathVariable UUID caseId,
                                                        @Valid @RequestBody StartTimerRequest request,
                                                        Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(timeEntryService.startTimer(caseId, request,
                SecurityUtils.currentUserId(authentication), SecurityUtils.currentOrgIds(authentication)));
    }

    @PostMapping("/timer/stop")
    public ResponseEntity<TimeEntryResponse> stopTimer(@PathVariable UUID caseId, Authentication authentication) {
        return ResponseEntity.ok(timeEntryService.stopTimer(caseId,
                SecurityUtils.currentUserId(authentication), SecurityUtils.currentOrgIds(authentication)));
    }
}
