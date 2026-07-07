package com.pravoos.user.identity.internal.controller;

import com.pravoos.user.identity.api.AuthResponse;
import com.pravoos.user.identity.api.LoginResponse;
import com.pravoos.user.identity.api.PasswordPolicyService;
import com.pravoos.user.identity.internal.dto.*;
import com.pravoos.user.identity.internal.security.RefreshCookieFactory;
import com.pravoos.user.identity.internal.service.AuthService;
import com.pravoos.user.identity.internal.service.PasswordResetService;
import com.pravoos.user.shared.exception.InvalidRefreshTokenException;
import com.pravoos.user.shared.exception.TooManyRequestsException;
import com.pravoos.user.shared.service.IpRateLimiter;
import com.pravoos.user.shared.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int LOGIN_MAX_PER_IP = 30;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(5);

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final PasswordResetService passwordResetService;
    private final PasswordPolicyService passwordPolicyService;
    private final IpRateLimiter ipRateLimiter;

    public AuthController(AuthService authService,
                          RefreshCookieFactory refreshCookieFactory,
                          PasswordResetService passwordResetService,
                          PasswordPolicyService passwordPolicyService,
                          IpRateLimiter ipRateLimiter) {
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.passwordResetService = passwordResetService;
        this.passwordPolicyService = passwordPolicyService;
        this.ipRateLimiter = ipRateLimiter;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        String clientIp = ClientIpResolver.resolve(httpRequest);
        if (!ipRateLimiter.allow("login", clientIp, LOGIN_MAX_PER_IP, LOGIN_WINDOW)) {
            throw new TooManyRequestsException();
        }
        LoginResult result = authService.login(request, clientIp, userAgent(httpRequest));
        if (result.mfaRequired()) {
            return ResponseEntity.ok(LoginResponse.mfaChallenge(result.mfaToken()));
        }
        return loginSuccess(result.tokens());
    }

    @PostMapping("/login/mfa")
    public ResponseEntity<LoginResponse> loginMfa(@Valid @RequestBody MfaLoginRequest request,
                                                  HttpServletRequest httpRequest) {
        String clientIp = ClientIpResolver.resolve(httpRequest);
        if (!ipRateLimiter.allow("login", clientIp, LOGIN_MAX_PER_IP, LOGIN_WINDOW)) {
            throw new TooManyRequestsException();
        }
        TokenResponse tokens = authService.completeMfaLogin(request.mfaToken(), request.code(), clientIp, userAgent(httpRequest));
        return loginSuccess(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return authResponse(authService.refresh(refreshToken, ClientIpResolver.resolve(httpRequest), userAgent(httpRequest)));
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

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordPolicyService.validate(request.password());
        passwordResetService.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<LoginResponse> loginSuccess(TokenResponse tokens) {
        AuthResponse auth = new AuthResponse(tokens.accessToken(), tokens.userId(), tokens.email(), tokens.role());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(tokens.refreshToken()).toString())
                .body(LoginResponse.authenticated(auth));
    }

    private ResponseEntity<AuthResponse> authResponse(TokenResponse tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(tokens.refreshToken()).toString())
                .body(new AuthResponse(tokens.accessToken(), tokens.userId(), tokens.email(), tokens.role()));
    }

    private static String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }
}
