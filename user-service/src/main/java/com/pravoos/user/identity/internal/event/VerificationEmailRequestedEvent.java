package com.pravoos.user.identity.internal.event;

public record VerificationEmailRequestedEvent(String email, String rawToken) {}
