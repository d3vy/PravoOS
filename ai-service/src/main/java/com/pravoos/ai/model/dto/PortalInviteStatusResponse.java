package com.pravoos.ai.model.dto;

import java.time.LocalDateTime;

public record PortalInviteStatusResponse(String status, String email, LocalDateTime expiresAt) {}
