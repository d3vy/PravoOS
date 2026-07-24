package com.pravoos.ai.practice.internal.dto;

import java.util.UUID;

public record SignerContext(UUID userId, String ip, String userAgent) {}
