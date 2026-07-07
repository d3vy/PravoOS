package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.LegalAiAnswer;
import com.pravoos.ai.core.api.LegalAiPort;
import com.pravoos.ai.practice.internal.dto.CaseDraftDto;
import com.pravoos.ai.practice.internal.dto.CaseDraftSummaryDto;
import com.pravoos.ai.practice.internal.dto.DraftTypeInfo;
import com.pravoos.ai.practice.internal.dto.GenerateDraftRequest;
import com.pravoos.ai.practice.internal.model.entity.CaseDraft;
import com.pravoos.ai.practice.internal.repository.jpa.CaseDraftRepository;
import com.pravoos.ai.shared.exception.DraftNotFoundException;
import com.pravoos.ai.shared.model.enums.DraftType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class DraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftService.class);

    private final CaseService caseService;
    private final LegalAiPort legalAiPort;
    private final CaseDraftRepository caseDraftRepository;

    public DraftService(CaseService caseService,
                        LegalAiPort legalAiPort,
                        CaseDraftRepository caseDraftRepository) {
        this.caseService = caseService;
        this.legalAiPort = legalAiPort;
        this.caseDraftRepository = caseDraftRepository;
    }

    public List<DraftTypeInfo> listDraftTypes() {
        return Arrays.stream(DraftType.values())
                .map(DraftTypeInfo::from)
                .toList();
    }

    public CaseDraftDto generate(UUID caseId, GenerateDraftRequest request, UUID lawyerId, List<UUID> orgIds) {
        legalAiPort.assertWithinQuota(lawyerId);
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        DraftType draftType = DraftType.fromId(request.draftType());
        log.info("Generating draft {} for case {} by lawyer {}", draftType.name(), caseId, lawyerId);

        LegalAiAnswer answer = legalAiPort.answerForCase(
                caseId, draftType.instruction(), "Выполни задачу.", lawyerId);
        log.info("LLM draft tokens for lawyer {}: total={}", lawyerId, answer.totalTokens());

        CaseDraft draft = new CaseDraft();
        draft.setCaseId(caseId);
        draft.setLawyerId(lawyerId);
        draft.setDraftType(draftType.name());
        draft.setTitle(draftType.displayName());
        draft.setContent(answer.content());

        CaseDraft saved = caseDraftRepository.save(draft);
        log.info("Draft {} generated with id {}", draftType.name(), saved.getId());
        return CaseDraftDto.from(saved);
    }

    public List<CaseDraftSummaryDto> findByCase(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        return caseDraftRepository.findByCaseIdOrderByCreatedAtDesc(caseId)
                .stream()
                .map(CaseDraftSummaryDto::from)
                .toList();
    }

    public CaseDraft requireVisibleDraft(UUID draftId, UUID lawyerId, List<UUID> orgIds) {
        CaseDraft draft = caseDraftRepository.findById(draftId)
                .orElseThrow(() -> new DraftNotFoundException(draftId));
        caseService.requireVisibleCase(draft.getCaseId(), lawyerId, orgIds);
        return draft;
    }
}
