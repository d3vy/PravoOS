package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.enums.CitationStatus;
import com.pravoos.ai.model.enums.CitationType;

public record CitationCheck(
        String raw,
        CitationType type,
        String normalized,
        CitationStatus status,
        String detail
) {}
