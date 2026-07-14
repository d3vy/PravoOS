package com.pravoos.user.push.internal.dto;

public record PushSubscriptionView(
        String endpoint,
        String p256dh,
        String auth
) {}
