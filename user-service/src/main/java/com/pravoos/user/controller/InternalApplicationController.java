package com.pravoos.user.controller;

import com.pravoos.user.service.ApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/applications")
public class InternalApplicationController {

    private final ApplicationService applicationService;

    public InternalApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
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
