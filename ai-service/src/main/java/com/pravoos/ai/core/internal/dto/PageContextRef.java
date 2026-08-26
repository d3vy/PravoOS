package com.pravoos.ai.core.internal.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PageContextRef(
    @Size(max = 256) String route, @Size(max = 32) String entityType, UUID entityId) {}
