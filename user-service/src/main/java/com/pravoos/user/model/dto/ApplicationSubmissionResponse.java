package com.pravoos.user.model.dto;

public record ApplicationSubmissionResponse(
        ApplicationResponse application,
        String statusToken
) {}
