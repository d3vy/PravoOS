package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.LegalAiAnswer;
import com.pravoos.ai.core.api.LegalAiPort;
import com.pravoos.ai.practice.internal.dto.CaseDraftDto;
import com.pravoos.ai.practice.internal.dto.CaseDraftSummaryDto;
import com.pravoos.ai.practice.internal.dto.CaseDraftVersionDto;
import com.pravoos.ai.practice.internal.dto.DraftTypeInfo;
import com.pravoos.ai.practice.internal.dto.GenerateDraftRequest;
import com.pravoos.ai.practice.internal.dto.RefineDraftRequest;
import com.pravoos.ai.practice.internal.dto.RefineDraftResponse;
import com.pravoos.ai.practice.internal.dto.UpdateDraftRequest;
import com.pravoos.ai.practice.internal.model.entity.CaseDraft;
import com.pravoos.ai.practice.internal.model.entity.CaseDraftVersion;
import com.pravoos.ai.practice.internal.repository.jpa.CaseDraftRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseDraftVersionRepository;
import com.pravoos.ai.shared.config.DraftEditingProperties;
import com.pravoos.ai.shared.exception.DraftEditingException;
import com.pravoos.ai.shared.exception.DraftNotFoundException;
import com.pravoos.ai.shared.model.enums.DraftType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class DraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftService.class);

    private final CaseService caseService;
    private final LegalAiPort legalAiPort;
    private final CaseDraftRepository caseDraftRepository;
    private final CaseDraftVersionRepository caseDraftVersionRepository;
    private final DraftEditingProperties properties;
    private final DraftService self;

    public DraftService(CaseService caseService,
                        LegalAiPort legalAiPort,
                        CaseDraftRepository caseDraftRepository,
                        CaseDraftVersionRepository caseDraftVersionRepository,
                        DraftEditingProperties properties,
                        @Lazy DraftService self) {
        this.caseService = caseService;
        this.legalAiPort = legalAiPort;
        this.caseDraftRepository = caseDraftRepository;
        this.caseDraftVersionRepository = caseDraftVersionRepository;
        this.properties = properties;
        this.self = self;
    }

    public List<DraftTypeInfo> listDraftTypes() {
        return Arrays.stream(DraftType.values())
                .map(DraftTypeInfo::from)
                .toList();
    }

    public CaseDraftDto generate(UUID caseId, GenerateDraftRequest request, UUID lawyerId, List<UUID> orgIds) {
        DraftType draftType = DraftType.fromId(request.draftType());
        self.assertGeneratable(caseId, lawyerId, orgIds);
        log.info("Generating draft {} for case {} by lawyer {}", draftType.name(), caseId, lawyerId);

        LegalAiAnswer answer = legalAiPort.answerForCase(
                caseId, draftType.instruction(), "Выполни задачу.", lawyerId);
        log.info("LLM draft tokens for lawyer {}: total={}", lawyerId, answer.totalTokens());

        return self.persistGeneratedDraft(caseId, lawyerId, draftType, answer.content());
    }

    @Transactional(readOnly = true)
    public void assertGeneratable(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        legalAiPort.assertWithinQuota(lawyerId);
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
    }

    @Transactional
    public CaseDraftDto persistGeneratedDraft(UUID caseId, UUID lawyerId, DraftType draftType, String content) {
        CaseDraft draft = new CaseDraft();
        draft.setCaseId(caseId);
        draft.setLawyerId(lawyerId);
        draft.setDraftType(draftType.name());
        draft.setTitle(draftType.displayName());
        draft.setContent(content);

        CaseDraft saved = caseDraftRepository.save(draft);
        snapshotVersion(saved, lawyerId, "Исходная генерация");
        log.info("Draft {} generated with id {}", draftType.name(), saved.getId());
        return CaseDraftDto.from(saved);
    }

    @Transactional(readOnly = true)
    public CaseDraftDto getDraft(UUID draftId, UUID lawyerId, List<UUID> orgIds) {
        return CaseDraftDto.from(requireVisibleDraft(draftId, lawyerId, orgIds));
    }

    @Transactional
    public CaseDraftDto updateContent(UUID draftId, UpdateDraftRequest request, UUID lawyerId, List<UUID> orgIds) {
        CaseDraft draft = requireVisibleDraft(draftId, lawyerId, orgIds);
        validateContentLength(request.content());
        draft.setContent(request.content());
        CaseDraft saved = caseDraftRepository.save(draft);
        snapshotVersion(saved, lawyerId, resolveNote(request.note(), "Ручная правка"));
        log.info("Draft {} updated by lawyer {}", draftId, lawyerId);
        return CaseDraftDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CaseDraftVersionDto> listVersions(UUID draftId, UUID lawyerId, List<UUID> orgIds) {
        requireVisibleDraft(draftId, lawyerId, orgIds);
        return caseDraftVersionRepository.findByDraftIdOrderByVersionNoDesc(draftId)
                .stream()
                .map(CaseDraftVersionDto::from)
                .toList();
    }

    @Transactional
    public CaseDraftDto restoreVersion(UUID draftId, UUID versionId, UUID lawyerId, List<UUID> orgIds) {
        CaseDraft draft = requireVisibleDraft(draftId, lawyerId, orgIds);
        CaseDraftVersion version = caseDraftVersionRepository.findByIdAndDraftId(versionId, draftId)
                .orElseThrow(() -> new DraftEditingException("Версия документа не найдена"));
        draft.setContent(version.getContent());
        CaseDraft saved = caseDraftRepository.save(draft);
        snapshotVersion(saved, lawyerId, "Восстановление версии #" + version.getVersionNo());
        log.info("Draft {} restored to version #{} by lawyer {}", draftId, version.getVersionNo(), lawyerId);
        return CaseDraftDto.from(saved);
    }

    public RefineDraftResponse refine(UUID draftId, RefineDraftRequest request, UUID lawyerId, List<UUID> orgIds) {
        legalAiPort.assertWithinQuota(lawyerId);
        CaseDraft draft = requireVisibleDraft(draftId, lawyerId, orgIds);
        String target = request.selectedText() != null && !request.selectedText().isBlank()
                ? request.selectedText()
                : draft.getContent();
        validateRefineLength(target);
        log.info("Refining draft {} for lawyer {} (selection={})", draftId, lawyerId,
                request.selectedText() != null && !request.selectedText().isBlank());
        LegalAiAnswer answer = legalAiPort.refineDraft(
                draft.getCaseId(), request.instruction(), target, lawyerId);
        return new RefineDraftResponse(answer.content());
    }

    private void snapshotVersion(CaseDraft draft, UUID lawyerId, String note) {
        CaseDraftVersion version = new CaseDraftVersion();
        version.setDraftId(draft.getId());
        version.setVersionNo(caseDraftVersionRepository.maxVersionNo(draft.getId()) + 1);
        version.setContent(draft.getContent());
        version.setNote(note);
        version.setCreatedBy(lawyerId);
        caseDraftVersionRepository.save(version);
    }

    private void validateContentLength(String content) {
        if (content.length() > properties.maxContentChars()) {
            throw new DraftEditingException(
                    "Документ превышает лимит в " + properties.maxContentChars() + " символов");
        }
    }

    private void validateRefineLength(String text) {
        if (text.length() > properties.maxRefineChars()) {
            throw new DraftEditingException(
                    "Фрагмент для доработки превышает лимит в " + properties.maxRefineChars() + " символов");
        }
    }

    private String resolveNote(String note, String fallback) {
        return note == null || note.isBlank() ? fallback : note.strip();
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
