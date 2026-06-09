package com.pravoos.ai.security;

import org.springframework.security.core.Authentication;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof String principal)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        try {
            return UUID.fromString(principal);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Authenticated principal is not a valid user id");
        }
    }
}
