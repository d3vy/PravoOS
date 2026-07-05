package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.shared.model.enums.CitationStatus;
import com.pravoos.ai.shared.model.enums.CitationType;

public record CitationCheck(
        String raw,
        CitationType type,
        String normalized,
        CitationStatus status,
        String detail
) {}
