package com.pravoos.user.identity.internal.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record DigestPreferenceRequest(@NotNull List<UUID> userIds) {}
