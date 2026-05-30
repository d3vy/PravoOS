package com.pravoos.user.controller;

import com.pravoos.user.model.dto.ApplyRequest;
import com.pravoos.user.model.dto.ApplicationResponse;
import com.pravoos.user.model.dto.LoginRequest;
import com.pravoos.user.model.dto.LoginResponse;
import com.pravoos.user.service.ApplicationService;
import com.pravoos.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ApplicationService applicationService;

    public AuthController(AuthService authService, ApplicationService applicationService) {
        this.authService = authService;
        this.applicationService = applicationService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApplicationResponse> apply(@Valid @RequestBody ApplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.submitApplication(request));
    }
}
