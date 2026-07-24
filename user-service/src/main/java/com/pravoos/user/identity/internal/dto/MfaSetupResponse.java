package com.pravoos.user.identity.internal.dto;

public record MfaSetupResponse(String secret, String otpauthUri) {}
