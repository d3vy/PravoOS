package com.pravoos.user.identity.internal.dto;

import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    UserRole role,
    UserStatus status,
    LocalDateTime createdAt,
    String fullName) {}
