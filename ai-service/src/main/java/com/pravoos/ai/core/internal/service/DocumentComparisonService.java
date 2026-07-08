package com.pravoos.ai.core.internal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.internal.dto.DiffChange;
import com.pravoos.ai.core.internal.dto.DocumentComparisonDto;
import com.pravoos.ai.core.internal.model.entity.DocumentComparison;
import com.pravoos.ai.core.internal.repository.jpa.DocumentComparisonRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentRef;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.shared.config.DocumentComparisonProperties;
import com.pravoos.ai.shared.exception.ContractReviewFailedException;
import com.pravoos.ai.shared.exception.DocumentComparisonNotFoundException;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.model.enums.ContractRiskLevel;
import com.pravoos.ai.shared.service.LlmQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentComparisonService {

    private static final Logger log = LoggerFactory.getLogger(DocumentComparisonService.class);
    private static final String USER_MESSAGE = "Оцени риски изменений между версиями и верни результат строго в JSON по заданной схеме.";
    private static final int COMMENT_MAX_LENGTH = 2000;

    private final CaseAccessProvider caseAccessProvider;
    private final DocumentAccess documentAccess;
    private final TextDiffService textDiffService;
    private final DocumentComparisonPrompt comparisonPrompt;
    private final LlmClient llmClient;
    private final LlmQuotaService llmQuotaService;
    private final DocumentComparisonRepository comparisonRepository;
    private final DocumentComparisonProperties properties;
    private final ObjectMapper objectMapper;

    public DocumentComparisonService(CaseAccessProvider caseAccessProvider,
                                     DocumentAccess documentAccess,
                                     TextDiffService textDiffService,
                                     DocumentComparisonPrompt comparisonPrompt,
                                     LlmClient llmClient,
                                     LlmQuotaService llmQuotaService,
                                     DocumentComparisonRepository comparisonRepository,
                                     DocumentComparisonProperties properties,
                                     ObjectMapper objectMapper) {
        this.caseAccessProvider = caseAccessProvider;
        this.documentAccess = documentAccess;
        this.textDiffService = textDiffService;
        this.comparisonPrompt = comparisonPrompt;
        this.llmClient = llmClient;
        this.llmQuotaService = llmQuotaService;
        this.comparisonRepository = comparisonRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocumentComparisonDto compare(UUID baseDocumentId, UUID revisedDocumentId, UUID lawyerId, List<UUID> orgIds) {
        if (baseDocumentId.equals(revisedDocumentId)) {
            throw new ContractReviewFailedException("Выберите две разные версии документа для сравнения");
        }
        llmQuotaService.assertWithinQuota(lawyerId);

        DocumentRef base = resolveCaseDocument(baseDocumentId, lawyerId, orgIds);
        DocumentRef revised = resolveCaseDocument(revisedDocumentId, lawyerId, orgIds);
        if (!base.caseId().equals(revised.caseId())) {
            throw new ContractReviewFailedException("Сравнивать можно только версии в рамках одного дела");
        }

        String baseText = extract(baseDocumentId);
        String revisedText = extract(revisedDocumentId);

        List<DiffChange> rawChanges = textDiffService.diff(baseText, revisedText);
        if (rawChanges.isEmpty()) {
            return persistIdentical(base, revised, lawyerId);
        }
        List<DiffChange> changes = truncate(rawChanges);

        String systemPrompt = comparisonPrompt.buildSystemPrompt(changes);
        LlmResult completion = llmClient.complete(systemPrompt, List.of(), USER_MESSAGE);
        llmQuotaService.recordUsage(lawyerId, completion.usage().totalTokens());
        log.info("Comparison LLM tokens for lawyer {}: total={}", lawyerId, completion.usage().totalTokens());

        ParsedAssessment assessment = parse(completion.content());
        List<DiffChange> assessed = applyAssessments(changes, assessment.byIndex());

        DocumentComparison saved = persist(base, revised, lawyerId, assessment.summary(), assessment.riskScore(), assessed);
        log.info("Comparison {} produced {} change(s) for case {}", saved.getId(), assessed.size(), base.caseId());
        return DocumentComparisonDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentComparisonDto> findByCase(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        caseAccessProvider.assertCaseVisible(caseId, lawyerId, orgIds);
        return comparisonRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream()
                .map(DocumentComparisonDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentComparisonDto get(UUID comparisonId, UUID lawyerId, List<UUID> orgIds) {
        DocumentComparison comparison = comparisonRepository.findById(comparisonId)
                .orElseThrow(() -> new DocumentComparisonNotFoundException(comparisonId));
        caseAccessProvider.assertCaseVisible(comparison.getCaseId(), lawyerId, orgIds);
        return DocumentComparisonDto.from(comparison);
    }

    private DocumentRef resolveCaseDocument(UUID documentId, UUID lawyerId, List<UUID> orgIds) {
        DocumentRef document = documentAccess.findForReview(documentId);
        if (document.caseId() == null) {
            log.warn("Lawyer {} attempted comparison on non-case document {}", lawyerId, documentId);
            throw new DocumentNotFoundException(documentId);
        }
        caseAccessProvider.assertCaseVisible(document.caseId(), lawyerId, orgIds);
        return document;
    }

    private String extract(UUID documentId) {
        String text = documentAccess.extractText(documentId);
        if (text.isBlank()) {
            throw new ContractReviewFailedException("Не удалось извлечь текст из документа для сравнения");
        }
        return budget(text);
    }

    private String budget(String text) {
        int limit = properties.maxInputChars();
        if (limit <= 0 || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit);
    }

    private List<DiffChange> truncate(List<DiffChange> rawChanges) {
        int maxChanges = properties.maxChanges();
        int maxChars = properties.changeMaxChars();
        List<DiffChange> result = new ArrayList<>();
        for (DiffChange change : rawChanges) {
            if (maxChanges > 0 && result.size() >= maxChanges) {
                break;
            }
            result.add(new DiffChange(result.size() + 1, change.type(),
                    truncateText(change.baseText(), maxChars),
                    truncateText(change.revisedText(), maxChars), null, null));
        }
        return result;
    }

    private String truncateText(String value, int max) {
        if (value == null) {
            return "";
        }
        return max <= 0 || value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private List<DiffChange> applyAssessments(List<DiffChange> changes, Map<Integer, Assessment> byIndex) {
        List<DiffChange> assessed = new ArrayList<>();
        for (DiffChange change : changes) {
            Assessment assessment = byIndex.get(change.order());
            ContractRiskLevel level = assessment != null ? assessment.level() : ContractRiskLevel.LOW;
            String comment = assessment != null ? assessment.comment() : "";
            assessed.add(change.withAssessment(level, comment));
        }
        return assessed;
    }

    private DocumentComparisonDto persistIdentical(DocumentRef base, DocumentRef revised, UUID lawyerId) {
        DocumentComparison saved = persist(base, revised, lawyerId,
                "Различий между версиями не обнаружено — тексты идентичны.", 0, List.of());
        return DocumentComparisonDto.from(saved);
    }

    private DocumentComparison persist(DocumentRef base, DocumentRef revised, UUID lawyerId,
                                       String summary, int riskScore, List<DiffChange> changes) {
        DocumentComparison comparison = new DocumentComparison();
        comparison.setCaseId(base.caseId());
        comparison.setBaseDocumentId(base.id());
        comparison.setRevisedDocumentId(revised.id());
        comparison.setLawyerId(lawyerId);
        comparison.setBaseDocumentTitle(base.title());
        comparison.setRevisedDocumentTitle(revised.title());
        comparison.setSummary(summary);
        comparison.setRiskScore((short) clampScore(riskScore));
        comparison.setChangeCount(changes.size());
        comparison.setHighRiskCount((int) changes.stream()
                .filter(change -> change.riskLevel() == ContractRiskLevel.HIGH)
                .count());
        comparison.setChanges(changes);
        return comparisonRepository.save(comparison);
    }

    private ParsedAssessment parse(String rawContent) {
        String json = extractJson(rawContent);
        try {
            JsonNode root = objectMapper.readTree(json);
            String summary = text(root.get("summary"), "Резюме не сформировано");
            int riskScore = clampScore(root.path("riskScore").asInt(0));
            Map<Integer, Assessment> byIndex = new HashMap<>();
            JsonNode changesNode = root.get("changes");
            if (changesNode != null && changesNode.isArray()) {
                for (JsonNode node : changesNode) {
                    int index = node.path("index").asInt(-1);
                    if (index < 0) {
                        continue;
                    }
                    byIndex.put(index, new Assessment(
                            ContractRiskLevel.fromString(text(node.get("level"), "LOW")),
                            truncateText(text(node.get("comment"), ""), COMMENT_MAX_LENGTH)));
                }
            }
            return new ParsedAssessment(summary, riskScore, byIndex);
        } catch (Exception e) {
            log.error("Failed to parse comparison JSON: {}", e.getMessage());
            throw new ContractReviewFailedException("Модель вернула некорректный результат анализа. Попробуйте снова.");
        }
    }

    private String extractJson(String content) {
        if (content == null) {
            throw new ContractReviewFailedException("Пустой ответ модели при сравнении версий");
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ContractReviewFailedException("Модель вернула ответ без JSON. Попробуйте снова.");
        }
        return content.substring(start, end + 1);
    }

    private String text(JsonNode node, String fallback) {
        if (node == null || node.isNull() || !node.isValueNode()) {
            return fallback;
        }
        String value = node.asText().strip();
        return value.isEmpty() ? fallback : value;
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private record ParsedAssessment(String summary, int riskScore, Map<Integer, Assessment> byIndex) {}

    private record Assessment(ContractRiskLevel level, String comment) {}
}
