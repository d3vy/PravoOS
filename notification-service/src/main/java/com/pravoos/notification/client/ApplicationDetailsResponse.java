package com.pravoos.notification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApplicationDetailsResponse(
        String fullName,
        String email,
        String specialization
) {}
