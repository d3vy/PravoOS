package com.pravoos.user.identity.api;

import java.time.LocalDateTime;

public record UserSessionSnapshot(
    String ipAddress, String userAgent, LocalDateTime createdAt, LocalDateTime lastUsedAt) {}
