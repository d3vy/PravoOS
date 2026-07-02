package com.pravoos.user.model.dto;

public record MfaSetupResponse(
        String secret,
        String otpauthUri
) {}
