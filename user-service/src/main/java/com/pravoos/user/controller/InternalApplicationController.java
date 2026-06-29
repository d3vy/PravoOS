package com.pravoos.user.controller;

import com.pravoos.user.model.dto.ApplicationResponse;
import com.pravoos.user.service.ApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/applications")
public class InternalApplicationController {

    private final ApplicationService applicationService;

    public InternalApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.getApplicationById(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id) {
        applicationService.approveApplication(id, null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve-force")
    public ResponseEntity<Void> approveForce(@PathVariable UUID id) {
        applicationService.approveApplication(id, null, true);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id) {
        applicationService.rejectApplication(id, null);
        return ResponseEntity.ok().build();
    }
}
