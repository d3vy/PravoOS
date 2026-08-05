package com.pravoos.user.registration.internal.controller;

import com.pravoos.user.identity.api.PasswordPolicyService;
import com.pravoos.user.registration.internal.dto.*;
import com.pravoos.user.registration.internal.service.ApplicationService;
import com.pravoos.user.registration.internal.service.EmailVerificationService;
import com.pravoos.user.shared.exception.TooManyRequestsException;
import com.pravoos.user.shared.service.IpRateLimiter;
import com.pravoos.user.shared.util.ClientIpResolver;
import com.pravoos.user.shared.util.EmailDeliverabilityValidator;
import com.pravoos.user.shared.util.EmailNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthApplicationController {

  private static final int APPLY_MAX_PER_IP = 5;
  private static final Duration APPLY_WINDOW = Duration.ofHours(1);

  private final ApplicationService applicationService;
  private final EmailVerificationService emailVerificationService;
  private final PasswordPolicyService passwordPolicyService;
  private final EmailDeliverabilityValidator emailDeliverabilityValidator;
  private final IpRateLimiter ipRateLimiter;

  public AuthApplicationController(
      ApplicationService applicationService,
      EmailVerificationService emailVerificationService,
      PasswordPolicyService passwordPolicyService,
      EmailDeliverabilityValidator emailDeliverabilityValidator,
      IpRateLimiter ipRateLimiter) {
    this.applicationService = applicationService;
    this.emailVerificationService = emailVerificationService;
    this.passwordPolicyService = passwordPolicyService;
    this.emailDeliverabilityValidator = emailDeliverabilityValidator;
    this.ipRateLimiter = ipRateLimiter;
  }

  @PostMapping("/apply")
  public ResponseEntity<ApplicationSubmissionResponse> apply(
      @Valid @RequestBody ApplyRequest request, HttpServletRequest httpRequest) {
    String clientIp = ClientIpResolver.resolve(httpRequest);
    if (!ipRateLimiter.allow("apply", clientIp, APPLY_MAX_PER_IP, APPLY_WINDOW)) {
      throw new TooManyRequestsException();
    }
    passwordPolicyService.validate(request.password());
    emailDeliverabilityValidator.validate(EmailNormalizer.normalize(request.email()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            applicationService.submitApplication(
                request, clientIp, httpRequest.getHeader("User-Agent")));
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
    if (request.password() != null && !request.password().isBlank()) {
      passwordPolicyService.validate(request.password());
    }
    emailDeliverabilityValidator.validate(EmailNormalizer.normalize(request.email()));
    return ResponseEntity.ok(applicationService.updateApplication(token, request));
  }

  @PostMapping("/verify-email")
  public ResponseEntity<Map<String, Boolean>> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest request) {
    emailVerificationService.verifyToken(request.token());
    return ResponseEntity.ok(Map.of("verified", true));
  }

  @PostMapping("/resend-verification")
  public ResponseEntity<Void> resendVerification(
      @Valid @RequestBody ResendVerificationRequest request) {
    emailVerificationService.resendVerification(request.email());
    return ResponseEntity.accepted().build();
  }
}
