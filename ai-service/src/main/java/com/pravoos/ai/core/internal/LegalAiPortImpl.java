package com.pravoos.ai.core.internal;

import com.pravoos.ai.config.DocumentProperties;
import com.pravoos.ai.core.api.LegalAiAnswer;
import com.pravoos.ai.core.api.LegalAiPort;
import com.pravoos.ai.core.internal.llm.LlmClient;
import com.pravoos.ai.core.internal.llm.LlmResult;
import com.pravoos.ai.core.internal.repository.ChunkMatch;
import com.pravoos.ai.core.internal.repository.VectorSearchRepository;
import com.pravoos.ai.core.internal.service.EmbeddingService;
import com.pravoos.ai.core.internal.service.LlmQuotaService;
import com.pravoos.ai.core.internal.service.RagService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class LegalAiPortImpl implements LegalAiPort {

    private final EmbeddingService embeddingService;
    private final VectorSearchRepository vectorSearchRepository;
    private final RagService ragService;
    private final LlmClient llmClient;
    private final LlmQuotaService llmQuotaService;
    private final DocumentProperties documentProperties;

    public LegalAiPortImpl(EmbeddingService embeddingService,
                           VectorSearchRepository vectorSearchRepository,
                           RagService ragService,
                           LlmClient llmClient,
                           LlmQuotaService llmQuotaService,
                           DocumentProperties documentProperties) {
        this.embeddingService = embeddingService;
        this.vectorSearchRepository = vectorSearchRepository;
        this.ragService = ragService;
        this.llmClient = llmClient;
        this.llmQuotaService = llmQuotaService;
        this.documentProperties = documentProperties;
    }

    @Override
    public void assertWithinQuota(UUID lawyerId) {
        llmQuotaService.assertWithinQuota(lawyerId);
    }

    @Override
    public float[] embed(String text) {
        return embeddingService.embed(text);
    }

    @Override
    public LegalAiAnswer answerForCase(UUID caseId, String instruction, String userMessage, UUID lawyerId) {
        float[] embedding = embeddingService.embed(instruction);
        List<ChunkMatch> matches = vectorSearchRepository
                .findTopKForCase(embedding, documentProperties.topKResults(), caseId);
        List<String> chunks = matches.stream().map(ChunkMatch::content).toList();
        String systemPrompt = ragService.buildWorkflowPrompt(instruction, chunks);
        LlmResult completion = llmClient.complete(systemPrompt, List.of(), userMessage);
        llmQuotaService.recordUsage(lawyerId, completion.usage().totalTokens());
        return new LegalAiAnswer(completion.content(), completion.usage().totalTokens());
    }
}
