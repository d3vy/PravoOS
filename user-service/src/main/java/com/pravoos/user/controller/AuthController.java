package com.pravoos.user.controller;

import com.pravoos.user.exception.InvalidRefreshTokenException;
import com.pravoos.user.exception.TooManyRequestsException;
import com.pravoos.user.model.dto.*;
import com.pravoos.user.security.RefreshCookieFactory;
import com.pravoos.user.service.*;
import com.pravoos.user.util.ClientIpResolver;
import com.pravoos.user.util.EmailDeliverabilityValidator;
import com.pravoos.user.util.EmailNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int APPLY_MAX_PER_IP = 5;
    private static final Duration APPLY_WINDOW = Duration.ofHours(1);
    private static final int LOGIN_MAX_PER_IP = 30;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(5);

    private final AuthService authService;
    private final ApplicationService applicationService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final EmailDeliverabilityValidator emailDeliverabilityValidator;
    private final IpRateLimiter ipRateLimiter;

    public AuthController(AuthService authService,
                          ApplicationService applicationService,
                          RefreshCookieFactory refreshCookieFactory,
                          EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService,
                          EmailDeliverabilityValidator emailDeliverabilityValidator,
                          IpRateLimiter ipRateLimiter) {
        this.authService = authService;
        this.applicationService = applicationService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.emailDeliverabilityValidator = emailDeliverabilityValidator;
        this.ipRateLimiter = ipRateLimiter;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        String clientIp = ClientIpResolver.resolve(httpRequest);
        if (!ipRateLimiter.allow("login", clientIp, LOGIN_MAX_PER_IP, LOGIN_WINDOW)) {
            throw new TooManyRequestsException();
        }
        return authResponse(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return authResponse(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
                .build();
    }

    @PostMapping("/apply")
    public ResponseEntity<ApplicationSubmissionResponse> apply(@Valid @RequestBody ApplyRequest request,
                                                               HttpServletRequest httpRequest) {
        String clientIp = ClientIpResolver.resolve(httpRequest);
        if (!ipRateLimiter.allow("apply", clientIp, APPLY_MAX_PER_IP, APPLY_WINDOW)) {
            throw new TooManyRequestsException();
        }
        emailDeliverabilityValidator.validate(EmailNormalizer.normalize(request.email()));
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.submitApplication(request));
    }

    @GetMapping("/application")
    public ResponseEntity<ApplicationResponse> getApplicationByStatusToken(
            @RequestHeader("X-Application-Token") String token) {
        return ResponseEntity.ok(applicationService.getApplicationByStatusToken(token));
    }

    @PutMapping("/application")
    public ResponseEntity<ApplicationResponse> updateApplication(
            @RequestHeader("X-Application-Token") String token,
            @Valid @RequestBody UpdateApplicationRequest request) {
        emailDeliverabilityValidator.validate(EmailNormalizer.normalize(request.email()));
        return ResponseEntity.ok(applicationService.updateApplication(token, request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, Boolean>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyToken(request.token());
        return ResponseEntity.ok(Map.of("verified", true));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resendVerification(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> authResponse(TokenResponse tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(tokens.refreshToken()).toString())
                .body(new AuthResponse(tokens.accessToken(), tokens.userId(), tokens.email(), tokens.role()));
    }
}
