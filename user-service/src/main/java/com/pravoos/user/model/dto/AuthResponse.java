package com.pravoos.user.model.dto;

import com.pravoos.user.model.enums.UserRole;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        UUID userId,
        String email,
        UserRole role
) {}
