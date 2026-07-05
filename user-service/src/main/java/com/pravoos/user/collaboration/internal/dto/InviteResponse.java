package com.pravoos.user.collaboration.internal.dto;

import com.pravoos.user.collaboration.internal.model.enums.InviteStatus;
import com.pravoos.user.collaboration.internal.model.enums.OrgRole;

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
