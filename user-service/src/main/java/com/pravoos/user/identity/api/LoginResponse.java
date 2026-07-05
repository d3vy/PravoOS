package com.pravoos.user.identity.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pravoos.user.identity.model.enums.UserRole;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        boolean mfaRequired,
        String mfaToken,
        String accessToken,
        UUID userId,
        String email,
        UserRole role
) {
    public static LoginResponse authenticated(AuthResponse auth) {
        return new LoginResponse(false, null, auth.accessToken(), auth.userId(), auth.email(), auth.role());
    }

    public static LoginResponse mfaChallenge(String mfaToken) {
        return new LoginResponse(true, mfaToken, null, null, null, null);
    }
}
