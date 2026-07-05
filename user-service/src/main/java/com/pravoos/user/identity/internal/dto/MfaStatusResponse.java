package com.pravoos.user.identity.internal.dto;

public record MfaStatusResponse(
        boolean enabled,
        boolean mandatory
) {}
