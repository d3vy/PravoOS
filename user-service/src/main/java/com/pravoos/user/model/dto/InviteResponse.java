package com.pravoos.user.model.dto;

import com.pravoos.user.model.enums.InviteStatus;
import com.pravoos.user.model.enums.OrgRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record InviteResponse(
        UUID id,
        String email,
        OrgRole orgRole,
        InviteStatus status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}
