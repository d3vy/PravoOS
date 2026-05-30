package com.pravoos.user.model.dto;

import com.pravoos.user.model.enums.UserRole;
import com.pravoos.user.model.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt,
        String fullName
) {}
