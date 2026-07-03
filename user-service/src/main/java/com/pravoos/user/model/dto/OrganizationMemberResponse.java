package com.pravoos.user.model.dto;

import com.pravoos.user.model.enums.OrgRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrganizationMemberResponse(
        UUID userId,
        String email,
        String fullName,
        OrgRole orgRole,
        LocalDateTime joinedAt
) {
}
