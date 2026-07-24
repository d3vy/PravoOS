package com.pravoos.user.push.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterPushSubscriptionRequest(
    @NotBlank @Size(max = 2048) @Pattern(regexp = "^https://.+") String endpoint,
    @NotBlank @Size(max = 255) String p256dh,
    @NotBlank @Size(max = 255) String auth,
    @Size(max = 255) String userAgent) {}
