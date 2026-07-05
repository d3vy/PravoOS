package com.pravoos.user.registration.internal.event;

public record ApplicationSubmittedSpringEvent(String email, String rawVerificationToken) {}
