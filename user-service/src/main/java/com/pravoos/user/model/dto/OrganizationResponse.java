package com.pravoos.user.model.dto;

import com.pravoos.user.model.enums.OrgRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        UUID ownerId,
        OrgRole myRole,
        long memberCount,
        LocalDateTime createdAt
) {
}
