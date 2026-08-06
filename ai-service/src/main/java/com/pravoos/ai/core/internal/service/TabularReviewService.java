package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.internal.dto.CreateTabularReviewRequest;
import com.pravoos.ai.core.internal.dto.TabularReviewDto;
import com.pravoos.ai.core.internal.dto.TabularReviewSummaryDto;
import com.pravoos.ai.core.internal.model.entity.TabularReview;
import com.pravoos.ai.core.internal.model.entity.TabularReviewCell;
import com.pravoos.ai.core.internal.model.entity.TabularReviewDocument;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewCellRepository;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewDocumentRepository;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentRef;
import com.pravoos.ai.shared.config.TabularReviewProperties;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.exception.TabularReviewFailedException;
import com.pravoos.ai.shared.exception.TabularReviewNotFoundException;
import com.pravoos.ai.shared.model.enums.TabularReviewStatus;
import com.pravoos.ai.shared.service.LlmQuotaService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TabularReviewService {

  private static final Logger log = LoggerFactory.getLogger(TabularReviewService.class);
  private static final String DEFAULT_TITLE = "Табличный разбор";
  private static final int TITLE_MAX_LENGTH = 300;

  private final CaseAccessProvider caseAccessProvider;
  private final DocumentAccess documentAccess;
  private final LlmQuotaService llmQuotaService;
  private final TabularReviewRepository reviewRepository;
  private final TabularReviewDocumentRepository reviewDocumentRepository;
  private final TabularReviewCellRepository cellRepository;
  private final TabularReviewRunner reviewRunner;
  private final TabularReviewProperties properties;

  public TabularReviewService(
      CaseAccessProvider caseAccessProvider,
      DocumentAccess documentAccess,
      LlmQuotaService llmQuotaService,
      TabularReviewRepository reviewRepository,
      TabularReviewDocumentRepository reviewDocumentRepository,
      TabularReviewCellRepository cellRepository,
      TabularReviewRunner reviewRunner,
      TabularReviewProperties properties) {
    this.caseAccessProvider = caseAccessProvider;
    this.documentAccess = documentAccess;
    this.llmQuotaService = llmQuotaService;
    this.reviewRepository = reviewRepository;
    this.reviewDocumentRepository = reviewDocumentRepository;
    this.cellRepository = cellRepository;
    this.reviewRunner = reviewRunner;
    this.properties = properties;
  }

  @Transactional
  public TabularReviewDto create(
      CreateTabularReviewRequest request, UUID lawyerId, List<UUID> orgIds) {
    caseAccessProvider.assertCaseOwned(request.caseId(), lawyerId);

    List<String> questions = normalizeQuestions(request.questions());
    List<UUID> documentIds = normalizeDocumentIds(request.documentIds());
    List<DocumentRef> documents = resolveCaseDocuments(documentIds, request.caseId(), lawyerId);
    llmQuotaService.assertQuotaHeadroom(lawyerId, documents.size());

    TabularReview review = new TabularReview();
    review.setCaseId(request.caseId());
    review.setLawyerId(lawyerId);
    review.setTitle(title(request.title()));
    review.setStatus(TabularReviewStatus.PENDING);
    review.setQuestions(questions);
    review.setDocumentCount(documents.size());
    review.setQuestionCount(questions.size());
    TabularReview saved = reviewRepository.save(review);

    List<TabularReviewDocument> reviewDocuments = new ArrayList<>();
    for (int position = 0; position < documents.size(); position++) {
      DocumentRef document = documents.get(position);
      TabularReviewDocument reviewDocument = new TabularReviewDocument();
      reviewDocument.setReviewId(saved.getId());
      reviewDocument.setDocumentId(document.id());
      reviewDocument.setDocumentTitle(document.title());
      reviewDocument.setPosition(position);
      reviewDocument.setStatus(TabularReviewStatus.PENDING);
      reviewDocuments.add(reviewDocument);
    }
    reviewDocumentRepository.saveAll(reviewDocuments);

    dispatchAfterCommit(saved.getId());
    log.info(
        "Tabular review {} queued: {} document(s) × {} question(s) for case {}",
        saved.getId(),
        documents.size(),
        questions.size(),
        request.caseId());

    return TabularReviewDto.from(saved, reviewDocuments, List.of());
  }

  @Transactional(readOnly = true)
  public TabularReviewDto get(UUID reviewId, UUID lawyerId, List<UUID> orgIds) {
    TabularReview review = requireVisibleReview(reviewId, lawyerId, orgIds);
    return TabularReviewDto.from(
        review,
        reviewDocumentRepository.findByReviewIdOrderByPositionAsc(reviewId),
        cellRepository.findByReviewId(reviewId));
  }

  @Transactional(readOnly = true)
  public List<TabularReviewSummaryDto> findByCase(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
    caseAccessProvider.assertCaseVisible(caseId, lawyerId, orgIds);
    return reviewRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream()
        .map(TabularReviewSummaryDto::from)
        .toList();
  }

  @Transactional
  public void delete(UUID reviewId, UUID lawyerId, List<UUID> orgIds) {
    TabularReview review = requireVisibleReview(reviewId, lawyerId, orgIds);
    caseAccessProvider.assertCaseOwned(review.getCaseId(), lawyerId);
    reviewRepository.delete(review);
  }

  @Transactional(readOnly = true)
  public TabularReview requireVisibleReview(UUID reviewId, UUID lawyerId, List<UUID> orgIds) {
    TabularReview review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new TabularReviewNotFoundException(reviewId));
    caseAccessProvider.assertCaseVisible(review.getCaseId(), lawyerId, orgIds);
    return review;
  }

  @Transactional(readOnly = true)
  public List<TabularReviewDocument> documentsOf(UUID reviewId) {
    return reviewDocumentRepository.findByReviewIdOrderByPositionAsc(reviewId);
  }

  @Transactional(readOnly = true)
  public List<TabularReviewCell> cellsOf(UUID reviewId) {
    return cellRepository.findByReviewId(reviewId);
  }

  private List<String> normalizeQuestions(List<String> rawQuestions) {
    List<String> questions =
        rawQuestions.stream()
            .filter(question -> question != null && !question.isBlank())
            .map(String::strip)
            .distinct()
            .toList();
    if (questions.isEmpty()) {
      throw new TabularReviewFailedException("Задайте хотя бы один вопрос-колонку");
    }
    if (questions.size() > properties.maxQuestions()) {
      throw new TabularReviewFailedException(
          "Максимум вопросов в одном разборе: " + properties.maxQuestions());
    }
    return questions;
  }

  private List<UUID> normalizeDocumentIds(List<UUID> rawDocumentIds) {
    List<UUID> documentIds = List.copyOf(new LinkedHashSet<>(rawDocumentIds));
    if (documentIds.size() > properties.maxDocuments()) {
      throw new TabularReviewFailedException(
          "Максимум документов в одном разборе: " + properties.maxDocuments());
    }
    return documentIds;
  }

  private List<DocumentRef> resolveCaseDocuments(
      List<UUID> documentIds, UUID caseId, UUID lawyerId) {
    List<DocumentRef> documents = new ArrayList<>();
    for (UUID documentId : documentIds) {
      DocumentRef document = documentAccess.findForReview(documentId);
      if (document.caseId() == null) {
        log.warn(
            "Lawyer {} attempted tabular review on non-case document {}", lawyerId, documentId);
        throw new DocumentNotFoundException(documentId);
      }
      if (!document.caseId().equals(caseId)) {
        throw new TabularReviewFailedException(
            "Все документы разбора должны относиться к одному делу");
      }
      documents.add(document);
    }
    return documents;
  }

  private String title(String rawTitle) {
    if (rawTitle == null || rawTitle.isBlank()) {
      return DEFAULT_TITLE;
    }
    String stripped = rawTitle.strip();
    return stripped.length() <= TITLE_MAX_LENGTH
        ? stripped
        : stripped.substring(0, TITLE_MAX_LENGTH);
  }

  private void dispatchAfterCommit(UUID reviewId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      reviewRunner.run(reviewId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            reviewRunner.run(reviewId);
          }
        });
  }
}
