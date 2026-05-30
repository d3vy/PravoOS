package com.pravoos.user.controller;

import com.pravoos.user.model.dto.ApplicationResponse;
import com.pravoos.user.model.dto.UserResponse;
import com.pravoos.user.service.AdminService;
import com.pravoos.user.service.ApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ApplicationService applicationService;
    private final AdminService adminService;

    public AdminController(ApplicationService applicationService, AdminService adminService) {
        this.applicationService = applicationService;
        this.adminService = adminService;
    }

    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationResponse>> getAllApplications() {
        return ResponseEntity.ok(applicationService.getAllApplications());
    }

    @GetMapping("/applications/pending")
    public ResponseEntity<List<ApplicationResponse>> getPendingApplications() {
        return ResponseEntity.ok(applicationService.getPendingApplications());
    }

    @PostMapping("/applications/{id}/approve")
    public ResponseEntity<ApplicationResponse> approve(@PathVariable UUID id,
                                                        Authentication authentication) {
        return ResponseEntity.ok(applicationService.approveApplication(id, currentUserId(authentication)));
    }

    @PostMapping("/applications/{id}/reject")
    public ResponseEntity<ApplicationResponse> reject(@PathVariable UUID id,
                                                       Authentication authentication) {
        return ResponseEntity.ok(applicationService.rejectApplication(id, currentUserId(authentication)));
    }

    @GetMapping("/users/lawyers")
    public ResponseEntity<List<UserResponse>> getActiveLawyers() {
        return ResponseEntity.ok(adminService.getActiveLawyers());
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString((String) authentication.getPrincipal());
    }
}
