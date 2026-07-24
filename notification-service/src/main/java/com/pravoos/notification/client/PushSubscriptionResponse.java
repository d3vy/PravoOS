package com.pravoos.notification.client;

public record PushSubscriptionResponse(String endpoint, String p256dh, String auth) {}
