package com.pravoos.user.controller;

import com.pravoos.user.model.dto.LawyerProfileResponse;
import com.pravoos.user.model.dto.UpdateProfileRequest;
import com.pravoos.user.security.SecurityUtils;
import com.pravoos.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
}
