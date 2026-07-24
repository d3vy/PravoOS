package com.pravoos.user.identity.internal.dto;

import com.pravoos.user.identity.model.enums.UserRole;
import java.util.UUID;

public record TokenResponse(
    String accessToken, String refreshToken, UUID userId, String email, UserRole role) {}
