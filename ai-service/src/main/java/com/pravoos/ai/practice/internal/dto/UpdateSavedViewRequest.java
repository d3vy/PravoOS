package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateSavedViewRequest(
    @NotBlank @Size(max = 80) String name,
    @NotBlank @Size(max = 8_000) String config,
    boolean sharedWithTeam,
    UUID orgId) {}
