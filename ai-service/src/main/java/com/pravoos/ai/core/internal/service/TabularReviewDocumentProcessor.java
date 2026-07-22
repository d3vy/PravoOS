package com.pravoos.ai.core.internal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.internal.dto.ReviewCitation;
import com.pravoos.ai.core.internal.model.entity.TabularReviewCell;
import com.pravoos.ai.document.api.DocumentChunkMatch;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.shared.config.TabularReviewProperties;
import com.pravoos.ai.shared.exception.TabularReviewFailedException;
import com.pravoos.ai.shared.model.enums.ReviewAnswerConfidence;
import com.pravoos.ai.shared.service.LlmQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TabularReviewDocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(TabularReviewDocumentProcessor.class);
    private static final String USER_MESSAGE =
            "Ответь на все вопросы по переданному документу и верни результат строго в JSON по заданной схеме.";
    private static final String NOT_FOUND_ANSWER = "Не найдено в документе";

    private final DocumentRetrieval documentRetrieval;
    private final TabularReviewPrompt reviewPrompt;
    private final LlmClient llmClient;
    private final LlmQuotaService llmQuotaService;
    private final TabularReviewWriter reviewWriter;
    private final TabularReviewProperties properties;
    private final ObjectMapper objectMapper;

    public TabularReviewDocumentProcessor(DocumentRetrieval documentRetrieval,
                                          TabularReviewPrompt reviewPrompt,
                                          LlmClient llmClient,
                                          LlmQuotaService llmQuotaService,
                                          TabularReviewWriter reviewWriter,
                                          TabularReviewProperties properties,
                                          ObjectMapper objectMapper) {
        this.documentRetrieval = documentRetrieval;
        this.reviewPrompt = reviewPrompt;
        this.llmClient = llmClient;
        this.llmQuotaService = llmQuotaService;
        this.reviewWriter = reviewWriter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean process(UUID reviewId, UUID documentId, String documentTitle,
                           List<String> questions, UUID lawyerId) {
        try {
            reviewWriter.markDocumentRunning(reviewId, documentId);

            List<Fragment> fragments = retrieveFragments(documentId, questions);
            List<TabularReviewCell> cells = fragments.isEmpty()
                    ? emptyCells(reviewId, documentId, questions)
                    : answerCells(reviewId, documentId, documentTitle, questions, fragments, lawyerId);

            reviewWriter.saveDocumentResult(reviewId, documentId, cells);
            return true;
        } catch (RuntimeException e) {
            log.warn("Tabular review {} failed on document {}: {}", reviewId, documentId, e.getMessage());
            reviewWriter.markDocumentFailed(reviewId, documentId, e.getMessage());
            return false;
        }
    }

    private List<Fragment> retrieveFragments(UUID documentId, List<String> questions) {
        Map<UUID, Fragment> byChunk = new LinkedHashMap<>();
        for (String question : questions) {
            List<DocumentChunkMatch> matches =
                    documentRetrieval.retrieveInDocument(question, properties.topKPerQuestion(), documentId);
            for (DocumentChunkMatch match : matches) {
                byChunk.merge(match.chunkId(),
                        new Fragment(match.chunkIndex(), match.content(), match.score()),
                        (existing, candidate) -> existing.score() >= candidate.score() ? existing : candidate);
            }
        }

        List<Fragment> ordered = new ArrayList<>(byChunk.values());
        ordered.sort(Comparator.comparingInt(Fragment::chunkIndex));
        return budget(ordered);
    }

    private List<Fragment> budget(List<Fragment> fragments) {
        int limit = properties.contextMaxChars();
        if (limit <= 0) {
            return fragments;
        }
        List<Fragment> kept = new ArrayList<>();
        int used = 0;
        for (Fragment fragment : fragments) {
            int length = fragment.content().length();
            if (used + length > limit && !kept.isEmpty()) {
                break;
            }
            kept.add(fragment);
            used += length;
        }
        return kept;
    }

    private List<TabularReviewCell> answerCells(UUID reviewId, UUID documentId, String documentTitle,
                                                List<String> questions, List<Fragment> fragments, UUID lawyerId) {
        String systemPrompt = reviewPrompt.buildSystemPrompt(
                documentTitle, fragments.stream().map(Fragment::content).toList(), questions);

        LlmResult completion = llmClient.complete(systemPrompt, List.of(), USER_MESSAGE);
        llmQuotaService.recordUsage(lawyerId, completion.usage().totalTokens());
        log.info("Tabular review {} document {} tokens: total={}",
                reviewId, documentId, completion.usage().totalTokens());

        Map<Integer, ParsedAnswer> byQuestion = parse(completion.content());

        List<TabularReviewCell> cells = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            ParsedAnswer parsed = byQuestion.get(index + 1);
            cells.add(parsed == null
                    ? newCell(reviewId, documentId, index, NOT_FOUND_ANSWER, ReviewAnswerConfidence.NOT_FOUND, List.of())
                    : newCell(reviewId, documentId, index, parsed.answer(), parsed.confidence(),
                            toCitations(parsed.sources(), fragments)));
        }
        return cells;
    }

    private List<TabularReviewCell> emptyCells(UUID reviewId, UUID documentId, List<String> questions) {
        List<TabularReviewCell> cells = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            cells.add(newCell(reviewId, documentId, index, NOT_FOUND_ANSWER,
                    ReviewAnswerConfidence.NOT_FOUND, List.of()));
        }
        return cells;
    }

    private List<ReviewCitation> toCitations(List<Integer> sources, List<Fragment> fragments) {
        List<ReviewCitation> citations = new ArrayList<>();
        for (Integer source : sources) {
            if (source == null || source < 1 || source > fragments.size()) {
                continue;
            }
            Fragment fragment = fragments.get(source - 1);
            citations.add(new ReviewCitation(fragment.chunkIndex(),
                    truncate(fragment.content(), properties.quoteMaxChars())));
        }
        return citations;
    }

    private TabularReviewCell newCell(UUID reviewId, UUID documentId, int questionIndex, String answer,
                                      ReviewAnswerConfidence confidence, List<ReviewCitation> citations) {
        TabularReviewCell cell = new TabularReviewCell();
        cell.setReviewId(reviewId);
        cell.setDocumentId(documentId);
        cell.setQuestionIndex(questionIndex);
        cell.setAnswer(truncate(answer, properties.answerMaxChars()));
        cell.setConfidence(confidence);
        cell.setCitations(citations);
        return cell;
    }

    private Map<Integer, ParsedAnswer> parse(String rawContent) {
        String json = extractJson(rawContent);
        try {
            JsonNode answers = objectMapper.readTree(json).get("answers");
            Map<Integer, ParsedAnswer> byQuestion = new LinkedHashMap<>();
            if (answers == null || !answers.isArray()) {
                return byQuestion;
            }
            for (JsonNode node : answers) {
                int questionNumber = node.path("question").asInt(-1);
                if (questionNumber < 1) {
                    continue;
                }
                ReviewAnswerConfidence confidence =
                        ReviewAnswerConfidence.fromString(node.path("confidence").asText(null));
                String answer = node.path("answer").asText("").strip();
                if (answer.isEmpty()) {
                    answer = NOT_FOUND_ANSWER;
                    confidence = ReviewAnswerConfidence.NOT_FOUND;
                }
                byQuestion.put(questionNumber, new ParsedAnswer(answer, confidence, sources(node.get("sources"))));
            }
            return byQuestion;
        } catch (TabularReviewFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse tabular review JSON: {}", e.getMessage());
            throw new TabularReviewFailedException("Модель вернула некорректный результат разбора");
        }
    }

    private List<Integer> sources(JsonNode sourcesNode) {
        if (sourcesNode == null || !sourcesNode.isArray()) {
            return List.of();
        }
        List<Integer> sources = new ArrayList<>();
        for (JsonNode node : sourcesNode) {
            if (node.isIntegralNumber()) {
                sources.add(node.asInt());
            }
        }
        return sources;
    }

    private String extractJson(String content) {
        if (content == null) {
            throw new TabularReviewFailedException("Пустой ответ модели при разборе документа");
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new TabularReviewFailedException("Модель вернула ответ без JSON");
        }
        return content.substring(start, end + 1);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return max <= 0 || value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private record Fragment(int chunkIndex, String content, double score) {}

    private record ParsedAnswer(String answer, ReviewAnswerConfidence confidence, List<Integer> sources) {}
}
