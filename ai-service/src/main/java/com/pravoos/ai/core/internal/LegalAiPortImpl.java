package com.pravoos.ai.core.internal;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.api.LegalAiAnswer;
import com.pravoos.ai.core.api.LegalAiPort;
import com.pravoos.ai.core.api.SourceReference;
import com.pravoos.ai.core.internal.model.entity.AiResponse;
import com.pravoos.ai.core.internal.repository.jpa.AiResponseRepository;
import com.pravoos.ai.core.internal.service.DraftRefinePrompt;
import com.pravoos.ai.core.internal.service.FollowUpParser;
import com.pravoos.ai.core.internal.service.RagService;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.document.api.RetrievedChunk;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.service.LlmQuotaService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class LegalAiPortImpl implements LegalAiPort {

    private static final int FRAGMENT_MAX_LENGTH = 300;

    private final DocumentRetrieval documentRetrieval;
    private final RagService ragService;
    private final DraftRefinePrompt draftRefinePrompt;
    private final LlmClient llmClient;
    private final LlmQuotaService llmQuotaService;
    private final DocumentProperties documentProperties;
    private final AiResponseRepository aiResponseRepository;

    public LegalAiPortImpl(DocumentRetrieval documentRetrieval,
                           RagService ragService,
                           DraftRefinePrompt draftRefinePrompt,
                           LlmClient llmClient,
                           LlmQuotaService llmQuotaService,
                           DocumentProperties documentProperties,
                           AiResponseRepository aiResponseRepository) {
        this.documentRetrieval = documentRetrieval;
        this.ragService = ragService;
        this.draftRefinePrompt = draftRefinePrompt;
        this.llmClient = llmClient;
        this.llmQuotaService = llmQuotaService;
        this.documentProperties = documentProperties;
        this.aiResponseRepository = aiResponseRepository;
    }

    @Override
    public void assertWithinQuota(UUID lawyerId) {
        llmQuotaService.assertWithinQuota(lawyerId);
    }

    @Override
    public float[] embed(String text) {
        return llmClient.embed(text);
    }

    @Override
    public LegalAiAnswer answerForCase(UUID caseId, String instruction, String userMessage, UUID lawyerId) {
        List<RetrievedChunk> matches = documentRetrieval
                .retrieveForCase(instruction, documentProperties.topKResults(), caseId);
        List<String> chunks = matches.stream().map(RetrievedChunk::content).toList();
        String systemPrompt = ragService.buildWorkflowPrompt(instruction, chunks);
        LlmResult completion = llmClient.complete(systemPrompt, List.of(), userMessage);
        llmQuotaService.recordUsage(lawyerId, completion.usage().totalTokens());
        return new LegalAiAnswer(completion.content(), completion.usage().totalTokens());
    }

    @Override
    public LegalAiAnswer refineDraft(UUID caseId, String instruction, String currentText, UUID lawyerId) {
        List<RetrievedChunk> matches = documentRetrieval
                .retrieveForCase(instruction, documentProperties.topKResults(), caseId);
        List<String> chunks = matches.stream().map(RetrievedChunk::content).toList();
        String systemPrompt = draftRefinePrompt.build(instruction, currentText, chunks);
        LlmResult completion = llmClient.complete(
                systemPrompt, List.of(), "Верни только переработанный текст фрагмента без пояснений.");
        llmQuotaService.recordUsage(lawyerId, completion.usage().totalTokens());
        return new LegalAiAnswer(completion.content(), completion.usage().totalTokens());
    }

    @Override
    public AiResponseDto runCaseWorkflow(UUID caseId, UUID lawyerId, String workflowId,
                                         String query, String instruction) {
        List<RetrievedChunk> matches = documentRetrieval
                .retrieveForCase(instruction, documentProperties.topKResults(), caseId);

        List<String> chunks = matches.stream().map(RetrievedChunk::content).toList();
        List<SourceReference> sources = toSourceReferences(matches);

        String systemPrompt = ragService.buildWorkflowPrompt(instruction, chunks);
        LlmResult completion = llmClient.complete(systemPrompt, List.of(), instruction);
        llmQuotaService.recordUsage(lawyerId, completion.usage().totalTokens());
        FollowUpParser.ParsedAnswer parsed = FollowUpParser.parse(completion.content());

        AiResponse response = new AiResponse();
        response.setCaseId(caseId);
        response.setLawyerId(lawyerId);
        response.setWorkflowId(workflowId);
        response.setQuery(query);
        response.setResult(parsed.answer());
        response.setSources(sources);
        AiResponse saved = aiResponseRepository.save(response);
        return AiResponseDto.from(saved, parsed.followUps());
    }

    private List<SourceReference> toSourceReferences(List<RetrievedChunk> matches) {
        return matches.stream()
                .map(match -> new SourceReference(match.documentTitle(), truncate(match.content())))
                .toList();
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= FRAGMENT_MAX_LENGTH
                ? content
                : content.substring(0, FRAGMENT_MAX_LENGTH) + "...";
    }
}
