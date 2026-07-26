package com.pravoos.ai.core.api;

import java.util.List;
import java.util.UUID;

public interface CaseContextProvider {

  CaseContext loadContext(UUID caseId, UUID lawyerId, List<UUID> orgIds);
}
