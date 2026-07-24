package com.pravoos.user.identity.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionResponse(
    UUID id,
    String ipAddress,
    String userAgent,
    LocalDateTime createdAt,
    LocalDateTime lastUsedAt) {}
