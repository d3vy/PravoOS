package com.pravoos.ai.shared.dto;

import java.time.LocalDateTime;

public record PortalInviteStatusResponse(String status, String email, LocalDateTime expiresAt) {}
