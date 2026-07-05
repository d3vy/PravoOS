package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.api.CaseAccessProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.shared.config.ContractReviewProperties;
import com.pravoos.ai.shared.exception.ContractReviewFailedException;
import com.pravoos.ai.shared.exception.ContractReviewNotFoundException;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.core.internal.llm.LlmClient;
import com.pravoos.ai.core.internal.llm.LlmResult;
import com.pravoos.ai.core.internal.dto.ContractReviewDto;
import com.pravoos.ai.core.internal.dto.ContractRisk;
import com.pravoos.ai.core.internal.model.entity.ContractReview;
import com.pravoos.ai.core.internal.model.entity.Document;
import com.pravoos.ai.shared.model.enums.ContractRiskLevel;
import com.pravoos.ai.core.internal.pipeline.DocumentParser;
import com.pravoos.ai.core.internal.repository.jpa.ContractReviewRepository;
import com.pravoos.ai.core.internal.repository.jpa.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ContractReviewService {

    private static final Logger log = LoggerFactory.getLogger(ContractReviewService.class);
    private static final String USER_MESSAGE = "Проведи ревью договора и верни результат строго в JSON по заданной схеме.";
    private static final int CLAUSE_MAX_LENGTH = 500;
    private static final int TEXT_FIELD_MAX_LENGTH = 2000;

    private final CaseAccessProvider caseAccessProvider;
    private final DocumentRepository documentRepository;
    private final FileCryptoService fileCryptoService;
    private final DocumentParser documentParser;
    private final LlmClient llmClient;
    private final LlmQuotaService llmQuotaService;
    private final ContractReviewRepository contractReviewRepository;
    private final ContractReviewPrompt contractReviewPrompt;
    private final ContractReviewProperties properties;
    private final ObjectMapper objectMapper;

    public ContractReviewService(CaseAccessProvider caseAccessProvider,
                                 DocumentRepository documentRepository,
                                 FileCryptoService fileCryptoService,
                                 DocumentParser documentParser,
                                 LlmClient llmClient,
                                 LlmQuotaService llmQuotaService,
                                 ContractReviewRepository contractReviewRepository,
                                 ContractReviewPrompt contractReviewPrompt,
                                 ContractReviewProperties properties,
                                 ObjectMapper objectMapper) {
        this.caseAccessProvider = caseAccessProvider;
        this.documentRepository = documentRepository;
        this.fileCryptoService = fileCryptoService;
        this.documentParser = documentParser;
        this.llmClient = llmClient;
        this.llmQuotaService = llmQuotaService;
        this.contractReviewRepository = contractReviewRepository;
        this.contractReviewPrompt = contractReviewPrompt;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ContractReviewDto review(UUID documentId, UUID lawyerId, List<UUID> orgIds) {
        llmQuotaService.assertWithinQuota(lawyerId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (document.getCaseId() == null) {
            log.warn("Lawyer {} attempted contract review on non-case document {}", lawyerId, documentId);
            throw new DocumentNotFoundException(documentId);
        }
        caseAccessProvider.assertCaseVisible(document.getCaseId(), lawyerId, orgIds);

        String contractText = extractText(document);
        if (contractText.isBlank()) {
            throw new ContractReviewFailedException("Не удалось извлечь текст из документа для анализа");
        }
        String budgetedText = budget(contractText);

        String systemPrompt = contractReviewPrompt.buildSystemPrompt(budgetedText);
        LlmResult completion = llmClient.complete(systemPrompt, List.of(), USER_MESSAGE);
        llmQuotaService.recordUsage(lawyerId, completion.usage().totalTokens());
        log.info("Contract review LLM tokens for lawyer {}: total={}", lawyerId, completion.usage().totalTokens());

        ParsedReview parsed = parse(completion.content());
        ContractReview saved = persist(document, lawyerId, parsed);
        log.info("Contract review {} produced {} risk(s) for document {}",
                saved.getId(), saved.getFindings().size(), documentId);
        return ContractReviewDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ContractReviewDto> findByCase(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        caseAccessProvider.assertCaseVisible(caseId, lawyerId, orgIds);
        return contractReviewRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream()
                .map(ContractReviewDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractReviewDto get(UUID reviewId, UUID lawyerId, List<UUID> orgIds) {
        ContractReview review = contractReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ContractReviewNotFoundException(reviewId));
        caseAccessProvider.assertCaseVisible(review.getCaseId(), lawyerId, orgIds);
        return ContractReviewDto.from(review);
    }

    private String extractText(Document document) {
        Path path = Paths.get(document.getFilePath());
        if (!Files.isReadable(path)) {
            log.warn("Contract review failed: document {} has missing file on disk: {}", document.getId(), path);
            throw new DocumentNotFoundException(document.getId());
        }
        byte[] content = fileCryptoService.decryptFile(path);
        return documentParser.extractText(content, document.getFileType());
    }

    private String budget(String text) {
        int limit = properties.maxInputChars();
        if (limit <= 0 || text.length() <= limit) {
            return text;
        }
        log.info("Contract text trimmed from {} to {} chars for review", text.length(), limit);
        return text.substring(0, limit);
    }

    private ContractReview persist(Document document, UUID lawyerId, ParsedReview parsed) {
        ContractReview review = new ContractReview();
        review.setCaseId(document.getCaseId());
        review.setDocumentId(document.getId());
        review.setLawyerId(lawyerId);
        review.setDocumentTitle(document.getTitle());
        review.setSummary(parsed.summary());
        review.setRiskScore((short) parsed.riskScore());
        review.setHighRiskCount((int) parsed.risks().stream()
                .filter(risk -> risk.level() == ContractRiskLevel.HIGH)
                .count());
        review.setFindings(parsed.risks());
        return contractReviewRepository.save(review);
    }

    private ParsedReview parse(String rawContent) {
        String json = extractJson(rawContent);
        try {
            JsonNode root = objectMapper.readTree(json);
            String summary = text(root.get("summary"), "Резюме не сформировано");
            int riskScore = clampScore(root.path("riskScore").asInt(0));
            List<ContractRisk> risks = parseRisks(root.get("risks"));
            return new ParsedReview(summary, riskScore, risks);
        } catch (Exception e) {
            log.error("Failed to parse contract review JSON: {}", e.getMessage());
            throw new ContractReviewFailedException("Модель вернула некорректный результат анализа. Попробуйте снова.");
        }
    }

    private List<ContractRisk> parseRisks(JsonNode risksNode) {
        List<ContractRisk> risks = new ArrayList<>();
        if (risksNode == null || !risksNode.isArray()) {
            return risks;
        }
        int maxFindings = properties.maxFindings();
        for (JsonNode node : risksNode) {
            if (maxFindings > 0 && risks.size() >= maxFindings) {
                break;
            }
            String clause = truncate(text(node.get("clause"), ""), CLAUSE_MAX_LENGTH);
            if (clause.isBlank()) {
                continue;
            }
            risks.add(new ContractRisk(
                    clause,
                    truncate(text(node.get("category"), "Прочее"), CLAUSE_MAX_LENGTH),
                    ContractRiskLevel.fromString(text(node.get("level"), "LOW")),
                    truncate(text(node.get("explanation"), ""), TEXT_FIELD_MAX_LENGTH),
                    truncate(text(node.get("recommendation"), ""), TEXT_FIELD_MAX_LENGTH)));
        }
        return risks;
    }

    private String extractJson(String content) {
        if (content == null) {
            throw new ContractReviewFailedException("Пустой ответ модели при анализе договора");
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

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private record ParsedReview(String summary, int riskScore, List<ContractRisk> risks) {}
}
