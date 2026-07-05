package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.api.CaseAccessQuery;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CaseAccessQueryImpl implements CaseAccessQuery {

    private final CaseService caseService;
    private final CaseRepository caseRepository;

    public CaseAccessQueryImpl(CaseService caseService, CaseRepository caseRepository) {
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
