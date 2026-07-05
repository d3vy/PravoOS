package com.pravoos.user.identity.internal.controller;

import com.pravoos.common.web.SecurityUtils;
import com.pravoos.user.identity.api.LawyerProfileResponse;
import com.pravoos.user.identity.internal.dto.NotificationSettingsResponse;
import com.pravoos.user.identity.internal.dto.SessionResponse;
import com.pravoos.user.identity.internal.dto.UpdateNotificationSettingsRequest;
import com.pravoos.user.identity.internal.dto.UpdateProfileRequest;
import com.pravoos.user.identity.internal.service.RefreshTokenService;
import com.pravoos.user.identity.internal.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
