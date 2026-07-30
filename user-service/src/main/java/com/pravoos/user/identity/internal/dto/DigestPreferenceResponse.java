package com.pravoos.user.identity.internal.dto;

import java.util.List;
import java.util.UUID;

public record DigestPreferenceResponse(List<UUID> userIds) {}
