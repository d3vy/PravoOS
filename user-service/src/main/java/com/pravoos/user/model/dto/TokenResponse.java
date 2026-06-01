package com.pravoos.user.model.dto;

import com.pravoos.user.model.enums.UserRole;

import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        String email,
        UserRole role
) {}
