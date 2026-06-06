package com.pravoos.user.model.dto;

import com.pravoos.user.model.enums.ApplicationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        String email,
        String fullName,
        String barNumber,
        String specialization,
        String phone,
        ApplicationStatus status,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        boolean emailVerified
) {}
