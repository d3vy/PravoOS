package com.pravoos.user.model.dto;

public record LoginResult(
        boolean mfaRequired,
        String mfaToken,
        TokenResponse tokens
) {
    public static LoginResult mfaRequired(String mfaToken) {
        return new LoginResult(true, mfaToken, null);
    }

    public static LoginResult success(TokenResponse tokens) {
        return new LoginResult(false, null, tokens);
    }
}
