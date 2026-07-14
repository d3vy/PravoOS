package com.pravoos.user.push.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnregisterPushSubscriptionRequest(
        @NotBlank @Size(max = 2048) String endpoint
) {}
