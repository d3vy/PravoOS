package com.pravoos.ai.core.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CaseAccessProvider {

  void assertCaseVisible(UUID caseId, UUID lawyerId, List<UUID> orgIds);

  void assertCaseOwned(UUID caseId, UUID lawyerId);

  UUID caseOrgId(UUID caseId);

  Set<UUID> retainCasesOwnedBy(Set<UUID> caseIds, UUID lawyerId);
}
