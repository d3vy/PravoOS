package com.pravoos.ai.core.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProposedAction(UUID id, String toolName, String title, LocalDateTime expiresAt) {}
