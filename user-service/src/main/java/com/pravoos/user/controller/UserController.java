package com.pravoos.user.controller;

import com.pravoos.user.model.dto.LawyerProfileResponse;
import com.pravoos.user.model.dto.TelegramLinkResponse;
import com.pravoos.user.model.dto.UpdateProfileRequest;
import com.pravoos.common.web.SecurityUtils;
import com.pravoos.user.service.TelegramLinkService;
import com.pravoos.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final TelegramLinkService telegramLinkService;

    public UserController(UserService userService, TelegramLinkService telegramLinkService) {
        this.userService = userService;
        this.telegramLinkService = telegramLinkService;
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

    @PostMapping("/profile/telegram/link-code")
    public ResponseEntity<TelegramLinkResponse> createTelegramLinkCode(Authentication authentication) {
        return ResponseEntity.ok(telegramLinkService.createLinkCode(SecurityUtils.currentUserId(authentication)));
    }

    @DeleteMapping("/profile/telegram")
    public ResponseEntity<Void> unlinkTelegram(Authentication authentication) {
        telegramLinkService.unlink(SecurityUtils.currentUserId(authentication));
        return ResponseEntity.noContent().build();
    }
}
