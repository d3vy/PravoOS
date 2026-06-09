package com.pravoos.user.controller;

import com.pravoos.user.config.InternalSecretProperties;
import com.pravoos.user.service.ApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.UUID;

@RestController
@RequestMapping("/internal/applications")
public class InternalApplicationController {

    private final ApplicationService applicationService;
    private final InternalSecretProperties secretProperties;

    public InternalApplicationController(ApplicationService applicationService,
                                          InternalSecretProperties secretProperties) {
        this.applicationService = applicationService;
        this.secretProperties = secretProperties;
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id,
                                         @RequestHeader("X-Internal-Secret") String secret) {
        verifySecret(secret);
        applicationService.approveApplication(id, null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve-force")
    public ResponseEntity<Void> approveForce(@PathVariable UUID id,
                                             @RequestHeader("X-Internal-Secret") String secret) {
        verifySecret(secret);
        applicationService.approveApplication(id, null, true);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id,
                                        @RequestHeader("X-Internal-Secret") String secret) {
        verifySecret(secret);
        applicationService.rejectApplication(id, null);
        return ResponseEntity.ok().build();
    }

    private void verifySecret(String secret) {
        if (secret == null || !MessageDigest.isEqual(sha256(secretProperties.secret()), sha256(secret))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
