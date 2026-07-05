package com.pravoos.user.registration.internal.dto;

import com.pravoos.user.registration.internal.model.enums.ApplicationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        String email,
        String fullName,
        String specialization,
        String phone,
        ApplicationStatus status,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        boolean emailVerified
) {}
