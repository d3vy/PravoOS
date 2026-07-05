package com.pravoos.user.registration.internal.dto;

public record ApplicationSubmissionResponse(
        ApplicationResponse application,
        String statusToken
) {}
