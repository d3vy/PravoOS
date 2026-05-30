package com.pravoos.user.model.dto;

import com.pravoos.user.model.enums.UserRole;

import java.util.UUID;

public record LoginResponse(
        String token,
        UUID userId,
        String email,
        UserRole role
) {}
