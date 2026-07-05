package com.pravoos.user.collaboration.internal.dto;

import java.time.LocalDateTime;

public record TelegramLinkResponse(String code, String deepLink, LocalDateTime expiresAt) {}
