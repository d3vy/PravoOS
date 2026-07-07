package com.pravoos.user.registration.internal.controller;

import com.pravoos.common.web.SecurityUtils;
import com.pravoos.user.identity.api.LawyerProfileResponse;
import com.pravoos.user.registration.internal.dto.ApplicationResponse;
import com.pravoos.user.registration.internal.dto.ClientStatsResponse;
import com.pravoos.user.registration.internal.service.AdminService;
import com.pravoos.user.registration.internal.service.ApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final ApplicationService applicationService;
    private final AdminService adminService;

    public AdminController(ApplicationService applicationService, AdminService adminService) {
        this.applicationService = applicationService;
        this.adminService = adminService;
    }

    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationResponse>> getAllApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(applicationService.countAllApplications()))
                .body(applicationService.getAllApplications(page, size));
    }

    @GetMapping("/applications/pending")
    public ResponseEntity<List<ApplicationResponse>> getPendingApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(applicationService.countPendingApplications()))
                .body(applicationService.getPendingApplications(page, size));
    }

    @PostMapping("/applications/{id}/approve")
    public ResponseEntity<ApplicationResponse> approve(@PathVariable UUID id,
                                                        Authentication authentication) {
        return ResponseEntity.ok(applicationService.approveApplication(id, SecurityUtils.currentUserId(authentication)));
    }

    @PostMapping("/applications/{id}/approve-force")
    public ResponseEntity<ApplicationResponse> approveForce(@PathVariable UUID id,
                                                            Authentication authentication) {
        return ResponseEntity.ok(applicationService.approveApplication(id, SecurityUtils.currentUserId(authentication), true));
    }

    @PostMapping("/applications/{id}/reject")
    public ResponseEntity<ApplicationResponse> reject(@PathVariable UUID id,
                                                       Authentication authentication) {
        return ResponseEntity.ok(applicationService.rejectApplication(id, SecurityUtils.currentUserId(authentication)));
    }

    @GetMapping("/users/lawyers")
    public ResponseEntity<List<LawyerProfileResponse>> getActiveLawyers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(adminService.countActiveLawyers()))
                .body(adminService.getActiveLawyers(page, size));
    }

    @DeleteMapping("/users/lawyers/{id}")
    public ResponseEntity<Void> deleteLawyer(@PathVariable UUID id, Authentication authentication) {
        adminService.deleteLawyer(id, SecurityUtils.currentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats/clients")
    public ResponseEntity<ClientStatsResponse> getClientStats() {
        return ResponseEntity.ok(adminService.getClientStats());
    }
}
