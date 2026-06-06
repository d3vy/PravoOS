package com.pravoos.user.event;

public record PasswordResetRequestedEvent(String email, String rawToken) {}
