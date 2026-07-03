package com.pravoos.ai.service;

import com.pravoos.ai.config.DocumentProperties;
import com.pravoos.ai.llm.LlmClient;
import com.pravoos.ai.llm.LlmResult;
import com.pravoos.ai.model.dto.AiResponseDto;
import com.pravoos.ai.model.dto.RunWorkflowRequest;
import com.pravoos.ai.model.dto.SourceReference;
import com.pravoos.ai.model.dto.WorkflowInfo;
import com.pravoos.ai.model.entity.AiResponse;
import com.pravoos.ai.model.enums.BankruptcyWorkflow;
import com.pravoos.ai.repository.ChunkMatch;
import com.pravoos.ai.repository.VectorSearchRepository;
import com.pravoos.ai.repository.jpa.AiResponseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);
    private static final int FRAGMENT_MAX_LENGTH = 300;

    private final CaseService caseService;
    private final EmbeddingService embeddingService;
    private final VectorSearchRepository vectorSearchRepository;
    private final RagService ragService;
    private final LlmClient llmClient;
    private final AiResponseRepository aiResponseRepository;
    private final DocumentProperties documentProperties;
    private final LlmQuotaService llmQuotaService;

    public WorkflowService(CaseService caseService,
                           EmbeddingService embeddingService,
                           VectorSearchRepository vectorSearchRepository,
                           RagService ragService,
                           LlmClient llmClient,
                           AiResponseRepository aiResponseRepository,
                           DocumentProperties documentProperties,
                           LlmQuotaService llmQuotaService) {
        this.caseService = caseService;
        this.embeddingService = embeddingService;
        this.vectorSearchRepository = vectorSearchRepository;
        this.ragService = ragService;
        this.llmClient = llmClient;
        this.aiResponseRepository = aiResponseRepository;
        this.documentProperties = documentProperties;
        this.llmQuotaService = llmQuotaService;
    }

    public List<WorkflowInfo> listWorkflows() {
        return Arrays.stream(BankruptcyWorkflow.values())
                .map(WorkflowInfo::from)
                .toList();
    }

    public AiResponseDto run(UUID caseId, String workflowId, RunWorkflowRequest request,
                             UUID lawyerId, List<UUID> orgIds) {
        llmQuotaService.assertWithinQuota(lawyerId);
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        BankruptcyWorkflow workflow = BankruptcyWorkflow.fromId(workflowId);

        String question = request != null && request.question() != null ? request.question().trim() : null;
        String instruction = buildInstruction(workflow, question);
        log.info("Running workflow {} on case {} by lawyer {}", workflow.name(), caseId, lawyerId);

        float[] embedding = embeddingService.embed(instruction);
        List<ChunkMatch> matches = vectorSearchRepository
                .findTopKForCase(embedding, documentProperties.topKResults(), caseId);

        List<String> chunks = matches.stream().map(ChunkMatch::content).toList();
        List<SourceReference> sources = toSourceReferences(matches);

        String systemPrompt = ragService.buildWorkflowPrompt(instruction, chunks);
        LlmResult completion = llmClient.complete(systemPrompt, List.of(), instruction);
        llmQuotaService.recordUsage(lawyerId, completion.usage().totalTokens());
        FollowUpParser.ParsedAnswer parsed = FollowUpParser.parse(completion.content());
        log.info("LLM workflow tokens for lawyer {}: total={}", lawyerId, completion.usage().totalTokens());

        String query = question != null && !question.isBlank() ? question : workflow.displayName();
        AiResponse saved = saveResponse(caseId, lawyerId, workflow.name(), query, parsed.answer(), sources);
        log.info("Workflow {} produced response {} with {} source(s)", workflow.name(), saved.getId(), sources.size());
        return AiResponseDto.from(saved, parsed.followUps());
    }

    private AiResponse saveResponse(UUID caseId, UUID lawyerId, String workflowId,
                                    String query, String result, List<SourceReference> sources) {
        AiResponse response = new AiResponse();
        response.setCaseId(caseId);
        response.setLawyerId(lawyerId);
        response.setWorkflowId(workflowId);
        response.setQuery(query);
        response.setResult(result);
        response.setSources(sources);
        return aiResponseRepository.save(response);
    }

    private String buildInstruction(BankruptcyWorkflow workflow, String question) {
        if (question == null || question.isBlank()) {
            return workflow.instruction();
        }
        return workflow.instruction() + "\n\nДополнительный вопрос юриста: " + question;
    }

    private List<SourceReference> toSourceReferences(List<ChunkMatch> matches) {
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
