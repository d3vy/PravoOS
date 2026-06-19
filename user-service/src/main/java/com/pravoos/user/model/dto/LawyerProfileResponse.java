package com.pravoos.user.model.dto;

import java.util.UUID;

public record LawyerProfileResponse(
        UUID userId,
        String email,
        String fullName,
        String barNumber,
        String specialization,
        String phone,
        boolean telegramLinked
) {}
