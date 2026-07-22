package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.SavedViewScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSavedViewRequest(
        @NotNull SavedViewScope scope,
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 8_000) String config,
        boolean sharedWithTeam,
        UUID orgId
) {}
