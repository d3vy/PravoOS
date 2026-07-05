package com.pravoos.user.registration.internal.event;

public record VerificationEmailRequestedEvent(String email, String rawToken) {}
