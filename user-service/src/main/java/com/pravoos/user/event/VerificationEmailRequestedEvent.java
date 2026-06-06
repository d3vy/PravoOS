package com.pravoos.user.event;

public record VerificationEmailRequestedEvent(String email, String rawToken) {}
