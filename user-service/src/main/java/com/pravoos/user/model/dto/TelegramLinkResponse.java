package com.pravoos.user.model.dto;

import java.time.LocalDateTime;

public record TelegramLinkResponse(String code, String deepLink, LocalDateTime expiresAt) {}
