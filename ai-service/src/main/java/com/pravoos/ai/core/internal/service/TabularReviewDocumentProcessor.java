package com.pravoos.ai.core.internal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.internal.dto.ReviewCitation;
import com.pravoos.ai.core.internal.model.entity.TabularReviewCell;
import com.pravoos.ai.document.api.DocumentChunkMatch;
import com.pravoos.ai.document.api.DocumentChunkMatches;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.shared.config.TabularReviewProperties;
import com.pravoos.ai.shared.exception.TabularReviewFailedException;
import com.pravoos.ai.shared.model.enums.ReviewAnswerConfidence;
import com.pravoos.ai.shared.service.LlmQuotaService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TabularReviewDocumentProcessor {

  private static final Logger log = LoggerFactory.getLogger(TabularReviewDocumentProcessor.class);
  private static final String NOT_FOUND_ANSWER = "Не найдено в документе";
  private static final Pattern CODE_FENCE =
      Pattern.compile("```(?:json)?\\s*(.*?)```", Pattern.DOTALL);

  private final DocumentRetrieval documentRetrieval;
  private final TabularReviewPrompt reviewPrompt;
  private final LlmClient llmClient;
  private final LlmQuotaService llmQuotaService;
  private final TabularReviewWriter reviewWriter;
  private final TabularReviewProperties properties;
  private final ObjectMapper objectMapper;

  public TabularReviewDocumentProcessor(
      DocumentRetrieval documentRetrieval,
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

  public boolean process(
      UUID reviewId, UUID documentId, String documentTitle, List<String> questions, UUID lawyerId) {
    try {
      llmQuotaService.assertWithinQuota(lawyerId);
      reviewWriter.markDocumentRunning(reviewId, documentId);

      List<Fragment> fragments = retrieveFragments(documentId, questions, lawyerId);
      List<TabularReviewCell> cells =
          fragments.isEmpty()
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

  private List<Fragment> retrieveFragments(UUID documentId, List<String> questions, UUID lawyerId) {
    DocumentChunkMatches retrieved =
        documentRetrieval.retrieveInDocument(questions, properties.topKPerQuestion(), documentId);
    llmQuotaService.recordTokenUsage(lawyerId, retrieved.totalTokens());

    List<Fragment> fragments = new ArrayList<>();
    for (DocumentChunkMatch match : retrieved.matches()) {
      fragments.add(new Fragment(match.chunkIndex(), match.content(), match.score()));
    }
    return budget(fragments);
  }

  private List<Fragment> budget(List<Fragment> fragments) {
    int limit = properties.contextMaxChars();
    if (limit <= 0) {
      fragments.sort(Comparator.comparingInt(Fragment::chunkIndex));
      return fragments;
    }
    fragments.sort(Comparator.comparingDouble(Fragment::score).reversed());
    List<Fragment> kept = new ArrayList<>();
    int used = 0;
    for (Fragment fragment : fragments) {
      int length = fragment.content().length();
      if (used + length > limit && !kept.isEmpty()) {
        continue;
      }
      kept.add(fragment);
      used += length;
    }
    kept.sort(Comparator.comparingInt(Fragment::chunkIndex));
    return kept;
  }

  private List<TabularReviewCell> answerCells(
      UUID reviewId,
      UUID documentId,
      String documentTitle,
      List<String> questions,
      List<Fragment> fragments,
      UUID lawyerId) {
    String userMessage =
        reviewPrompt.buildUserMessage(
            documentTitle, fragments.stream().map(Fragment::content).toList(), questions);

    LlmResult completion =
        llmClient.complete(reviewPrompt.buildSystemPrompt(), List.of(), userMessage);
    llmQuotaService.recordUsage(lawyerId, completion.usage().totalTokens());
    log.info(
        "Tabular review {} document {} tokens: total={}",
        reviewId,
        documentId,
        completion.usage().totalTokens());

    Map<Integer, ParsedAnswer> byQuestion = parse(completion.content());

    List<TabularReviewCell> cells = new ArrayList<>();
    for (int index = 0; index < questions.size(); index++) {
      ParsedAnswer parsed = byQuestion.get(index + 1);
      cells.add(
          parsed == null
              ? newCell(
                  reviewId,
                  documentId,
                  index,
                  NOT_FOUND_ANSWER,
                  ReviewAnswerConfidence.NOT_FOUND,
                  List.of())
              : newCell(
                  reviewId,
                  documentId,
                  index,
                  parsed.answer(),
                  parsed.confidence(),
                  toCitations(parsed.sources(), fragments)));
    }
    return cells;
  }

  private List<TabularReviewCell> emptyCells(
      UUID reviewId, UUID documentId, List<String> questions) {
    List<TabularReviewCell> cells = new ArrayList<>();
    for (int index = 0; index < questions.size(); index++) {
      cells.add(
          newCell(
              reviewId,
              documentId,
              index,
              NOT_FOUND_ANSWER,
              ReviewAnswerConfidence.NOT_FOUND,
              List.of()));
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
      citations.add(
          new ReviewCitation(
              fragment.chunkIndex(), truncate(fragment.content(), properties.quoteMaxChars())));
    }
    return citations;
  }

  private TabularReviewCell newCell(
      UUID reviewId,
      UUID documentId,
      int questionIndex,
      String answer,
      ReviewAnswerConfidence confidence,
      List<ReviewCitation> citations) {
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
        byQuestion.put(
            questionNumber, new ParsedAnswer(answer, confidence, sources(node.get("sources"))));
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
    if (content == null || content.isBlank()) {
      throw new TabularReviewFailedException("Пустой ответ модели при разборе документа");
    }
    String fenced = unwrapCodeFence(content);
    String json = balancedObject(fenced != null ? fenced : content);
    if (json == null && fenced != null) {
      json = balancedObject(content);
    }
    if (json == null) {
      throw new TabularReviewFailedException("Модель вернула ответ без JSON");
    }
    return json;
  }

  private String unwrapCodeFence(String content) {
    Matcher matcher = CODE_FENCE.matcher(content);
    return matcher.find() ? matcher.group(1) : null;
  }

  private String balancedObject(String content) {
    int start = content.indexOf('{');
    if (start < 0) {
      return null;
    }
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int index = start; index < content.length(); index++) {
      char current = content.charAt(index);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (current == '\\') {
          escaped = true;
        } else if (current == '"') {
          inString = false;
        }
        continue;
      }
      if (current == '"') {
        inString = true;
      } else if (current == '{') {
        depth++;
      } else if (current == '}' && --depth == 0) {
        return content.substring(start, index + 1);
      }
    }
    return null;
  }

  private String truncate(String value, int max) {
    if (value == null) {
      return "";
    }
    return max <= 0 || value.length() <= max ? value : value.substring(0, max) + "…";
  }

  private record Fragment(int chunkIndex, String content, double score) {}

  private record ParsedAnswer(
      String answer, ReviewAnswerConfidence confidence, List<Integer> sources) {}
}
