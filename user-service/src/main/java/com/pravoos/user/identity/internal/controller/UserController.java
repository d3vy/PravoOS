package com.pravoos.user.identity.internal.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pravoos.common.web.SecurityUtils;
import com.pravoos.user.identity.api.LawyerProfileResponse;
import com.pravoos.user.identity.internal.dto.LanguageSettingsResponse;
import com.pravoos.user.identity.internal.dto.NotificationSettingsResponse;
import com.pravoos.user.identity.internal.dto.SessionResponse;
import com.pravoos.user.identity.internal.dto.UpdateLanguageRequest;
import com.pravoos.user.identity.internal.dto.UpdateNotificationSettingsRequest;
import com.pravoos.user.identity.internal.dto.UpdateProfileRequest;
import com.pravoos.user.identity.internal.service.RefreshTokenService;
import com.pravoos.user.identity.internal.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;

    public UserController(UserService userService,
            RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
    }

    @GetMapping("/profile")
    public ResponseEntity<LawyerProfileResponse> getProfile(Authentication authentication) {
        return ResponseEntity.ok(userService.getProfile(SecurityUtils.currentUserId(authentication)));
    }

    @PatchMapping("/profile")
    public ResponseEntity<LawyerProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(userService.updateProfile(SecurityUtils.currentUserId(authentication), request));
    }

    @GetMapping("/settings/notifications")
    public ResponseEntity<NotificationSettingsResponse> getNotificationSettings(Authentication authentication) {
        return ResponseEntity.ok(
                userService.getNotificationSettings(SecurityUtils.currentUserId(authentication)));
    }

    @PutMapping("/settings/notifications")
    public ResponseEntity<NotificationSettingsResponse> updateNotificationSettings(
            @Valid @RequestBody UpdateNotificationSettingsRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                userService.updateNotificationSettings(SecurityUtils.currentUserId(authentication), request));
    }

    @GetMapping("/settings/language")
    public ResponseEntity<LanguageSettingsResponse> getLanguage(Authentication authentication) {
        return ResponseEntity.ok(userService.getLanguage(SecurityUtils.currentUserId(authentication)));
    }

    @PutMapping("/settings/language")
    public ResponseEntity<LanguageSettingsResponse> updateLanguage(
            @Valid @RequestBody UpdateLanguageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                userService.updateLanguage(SecurityUtils.currentUserId(authentication), request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions(Authentication authentication) {
        return ResponseEntity.ok(refreshTokenService.listActiveSessions(SecurityUtils.currentUserId(authentication)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId, Authentication authentication) {
        refreshTokenService.revokeSession(SecurityUtils.currentUserId(authentication), sessionId);
        return ResponseEntity.noContent().build();
    }
}
