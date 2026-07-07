package com.pravoos.ai.core.api;

import java.util.List;
import java.util.UUID;

public interface AiResponseQuery {

    List<AiResponseDto> listVisibleByCase(UUID caseId, UUID lawyerId, List<UUID> orgIds);

    List<AiResponseDto> listByCase(UUID caseId);
}
