package com.pravoos.user.identity.api;

import java.util.UUID;

public record LawyerProfileResponse(
        UUID userId,
        String email,
        String fullName,
        String specialization,
        String phone,
        boolean telegramLinked
) {}
