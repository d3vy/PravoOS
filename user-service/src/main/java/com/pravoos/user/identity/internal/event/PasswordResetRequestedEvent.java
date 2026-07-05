package com.pravoos.user.identity.internal.event;

public record PasswordResetRequestedEvent(String email, String rawToken) {}
