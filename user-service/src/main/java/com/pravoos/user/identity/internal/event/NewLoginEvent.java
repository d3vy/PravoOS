package com.pravoos.user.identity.internal.event;

import java.time.LocalDateTime;

public record NewLoginEvent(
    String email, String ipAddress, String userAgent, LocalDateTime occurredAt) {}
