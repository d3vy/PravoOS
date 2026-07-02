package com.pravoos.user.model.dto;

public record MfaStatusResponse(
        boolean enabled,
        boolean mandatory
) {}
