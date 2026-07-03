package com.pravoos.ai.service;

import com.pravoos.ai.config.DocumentProperties;
import com.pravoos.ai.exception.DraftNotFoundException;
import com.pravoos.ai.llm.LlmClient;
import com.pravoos.ai.llm.LlmResult;
import com.pravoos.ai.model.dto.CaseDraftDto;
import com.pravoos.ai.model.dto.CaseDraftSummaryDto;
import com.pravoos.ai.model.dto.DraftTypeInfo;
import com.pravoos.ai.model.dto.GenerateDraftRequest;
import com.pravoos.ai.model.entity.CaseDraft;
import com.pravoos.ai.model.enums.DraftType;
import com.pravoos.ai.repository.ChunkMatch;
import com.pravoos.ai.repository.VectorSearchRepository;
import com.pravoos.ai.repository.jpa.CaseDraftRepository;
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
    private final EmbeddingService embeddingService;
    private final VectorSearchRepository vectorSearchRepository;
    private final RagService ragService;
    private final LlmClient llmClient;
    private final CaseDraftRepository caseDraftRepository;
    private final DocumentProperties documentProperties;
    private final LlmQuotaService llmQuotaService;

    public DraftService(CaseService caseService,
                        EmbeddingService embeddingService,
                        VectorSearchRepository vectorSearchRepository,
                        RagService ragService,
                        LlmClient llmClient,
                        CaseDraftRepository caseDraftRepository,
                        DocumentProperties documentProperties,
                        LlmQuotaService llmQuotaService) {
        this.caseService = caseService;
        this.embeddingService = embeddingService;
        this.vectorSearchRepository = vectorSearchRepository;
        this.ragService = ragService;
        this.llmClient = llmClient;
        this.caseDraftRepository = caseDraftRepository;
        this.documentProperties = documentProperties;
        this.llmQuotaService = llmQuotaService;
    }

    public List<DraftTypeInfo> listDraftTypes() {
        return Arrays.stream(DraftType.values())
                .map(DraftTypeInfo::from)
                .toList();
    }

    public CaseDraftDto generate(UUID caseId, GenerateDraftRequest request, UUID lawyerId, List<UUID> orgIds) {
        llmQuotaService.assertWithinQuota(lawyerId);
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        DraftType draftType = DraftType.fromId(request.draftType());
        log.info("Generating draft {} for case {} by lawyer {}", draftType.name(), caseId, lawyerId);

        float[] embedding = embeddingService.embed(draftType.instruction());
        List<ChunkMatch> matches = vectorSearchRepository
                .findTopKForCase(embedding, documentProperties.topKResults(), caseId);

        List<String> chunks = matches.stream().map(ChunkMatch::content).toList();
        String systemPrompt = ragService.buildWorkflowPrompt(draftType.instruction(), chunks);
        LlmResult completion = llmClient.complete(systemPrompt, List.of(), "Выполни задачу.");
        llmQuotaService.recordUsage(lawyerId, completion.usage().totalTokens());
        log.info("LLM draft tokens for lawyer {}: total={}", lawyerId, completion.usage().totalTokens());

        CaseDraft draft = new CaseDraft();
        draft.setCaseId(caseId);
        draft.setLawyerId(lawyerId);
        draft.setDraftType(draftType.name());
        draft.setTitle(draftType.displayName());
        draft.setContent(completion.content());

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
