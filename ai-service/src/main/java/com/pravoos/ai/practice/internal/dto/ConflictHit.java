package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.ConflictSource;
import java.util.UUID;

public record ConflictHit(
    ConflictSource source,
    String matchedName,
    UUID clientId,
    UUID caseId,
    String caseTitle,
    String role) {}
