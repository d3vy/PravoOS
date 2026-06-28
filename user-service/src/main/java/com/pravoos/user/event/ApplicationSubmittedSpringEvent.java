package com.pravoos.user.event;

public record ApplicationSubmittedSpringEvent(String email, String rawVerificationToken) {}
