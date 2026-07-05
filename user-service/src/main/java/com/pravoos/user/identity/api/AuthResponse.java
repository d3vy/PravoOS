package com.pravoos.user.identity.api;

import com.pravoos.user.identity.model.enums.UserRole;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        UUID userId,
        String email,
        UserRole role
) {}
