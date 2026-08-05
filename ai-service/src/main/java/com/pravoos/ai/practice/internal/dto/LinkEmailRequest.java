package com.pravoos.ai.practice.internal.dto;

import java.util.UUID;

public record LinkEmailRequest(UUID caseId, UUID clientId) {}
