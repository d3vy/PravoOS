package com.pravoos.user.identity.internal.dto;

import java.util.UUID;

public record IssuedRefreshToken(String rawToken, UUID sessionId) {}
