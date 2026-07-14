package com.pravoos.user.push.internal.dto;

public record PushConfigResponse(
        boolean configured,
        String publicKey
) {
    public static PushConfigResponse disabled() {
        return new PushConfigResponse(false, null);
    }
}
