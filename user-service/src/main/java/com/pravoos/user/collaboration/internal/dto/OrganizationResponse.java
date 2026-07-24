package com.pravoos.user.collaboration.internal.dto;

import com.pravoos.user.collaboration.internal.model.enums.OrgRole;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrganizationResponse(
    UUID id,
    String name,
    UUID ownerId,
    OrgRole myRole,
    long memberCount,
    LocalDateTime createdAt) {}
