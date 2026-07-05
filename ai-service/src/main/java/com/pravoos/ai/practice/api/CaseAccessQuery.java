package com.pravoos.ai.practice.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CaseAccessQuery {

    void assertCaseVisible(UUID caseId, UUID lawyerId, List<UUID> orgIds);

    Set<UUID> retainCasesOwnedBy(Set<UUID> caseIds, UUID lawyerId);
}
