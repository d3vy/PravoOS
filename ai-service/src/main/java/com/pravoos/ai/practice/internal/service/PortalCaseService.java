package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.exception.CaseNotFoundException;
import com.pravoos.ai.model.dto.CaseHearingEventResponse;
import com.pravoos.ai.model.dto.PortalCaseDetailResponse;
import com.pravoos.ai.model.dto.PortalCaseResponse;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PortalCaseService {

    private static final Logger log = LoggerFactory.getLogger(PortalCaseService.class);

    private final CaseRepository caseRepository;
    private final CaseHearingEventRepository hearingEventRepository;

    public PortalCaseService(CaseRepository caseRepository,
                             CaseHearingEventRepository hearingEventRepository) {
        this.caseRepository = caseRepository;
        this.hearingEventRepository = hearingEventRepository;
    }

    @Transactional(readOnly = true)
    public List<PortalCaseResponse> findCases(List<UUID> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return List.of();
        }
        return caseRepository.findByClientIdInOrderByCreatedAtDesc(clientIds).stream()
                .map(PortalCaseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PortalCaseDetailResponse getCase(UUID caseId, List<UUID> clientIds) {
        Case caseEntity = requireClientCase(caseId, clientIds);
        List<CaseHearingEventResponse> hearings =
                hearingEventRepository.findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId).stream()
                        .map(CaseHearingEventResponse::from)
                        .toList();
        return PortalCaseDetailResponse.from(caseEntity, hearings);
    }

    @Transactional(readOnly = true)
    public Case requireClientCase(UUID caseId, List<UUID> clientIds) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
        UUID caseClientId = caseEntity.getClientId();
        if (caseClientId == null || clientIds == null || !clientIds.contains(caseClientId)) {
            log.warn("Portal access denied: case {} not owned by client scope {}", caseId, clientIds);
            throw new CaseNotFoundException(caseId);
        }
        return caseEntity;
    }
}
