package com.pravoos.user.controller;

import com.pravoos.user.config.InternalSecretProperties;
import com.pravoos.user.service.ApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id,
                                        @RequestHeader("X-Internal-Secret") String secret) {
        verifySecret(secret);
        applicationService.rejectApplication(id, null);
        return ResponseEntity.ok().build();
    }

    private void verifySecret(String secret) {
        byte[] expected = secretProperties.secret().getBytes(StandardCharsets.UTF_8);
        byte[] actual = secret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
