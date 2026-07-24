package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseAccessProviderImpl implements CaseAccessProvider {

  private final CaseService caseService;
  private final CaseRepository caseRepository;

  public CaseAccessProviderImpl(CaseService caseService, CaseRepository caseRepository) {
    this.caseService = caseService;
    this.caseRepository = caseRepository;
  }

  @Override
  public void assertCaseVisible(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
    caseService.requireVisibleCase(caseId, lawyerId, orgIds);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> retainCasesOwnedBy(Set<UUID> caseIds, UUID lawyerId) {
    if (caseIds == null || caseIds.isEmpty()) {
      return Set.of();
    }
    return caseRepository.findAllById(caseIds).stream()
        .filter(caseEntity -> caseEntity.getLawyerId().equals(lawyerId))
        .map(Case::getId)
        .collect(Collectors.toSet());
  }
}
