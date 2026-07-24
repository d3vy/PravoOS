package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.internal.model.entity.TabularReview;
import com.pravoos.ai.core.internal.model.entity.TabularReviewDocument;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewDocumentRepository;
import com.pravoos.ai.shared.model.enums.TabularReviewStatus;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class TabularReviewRunner {

  private static final Logger log = LoggerFactory.getLogger(TabularReviewRunner.class);

  private final TabularReviewDocumentProcessor documentProcessor;
  private final TabularReviewDocumentRepository reviewDocumentRepository;
  private final TabularReviewWriter reviewWriter;
  private final ThreadPoolTaskExecutor documentExecutor;

  public TabularReviewRunner(
      TabularReviewDocumentProcessor documentProcessor,
      TabularReviewDocumentRepository reviewDocumentRepository,
      TabularReviewWriter reviewWriter,
      @Qualifier("tabularReviewCellExecutor") ThreadPoolTaskExecutor tabularReviewCellExecutor) {
    this.documentProcessor = documentProcessor;
    this.reviewDocumentRepository = reviewDocumentRepository;
    this.reviewWriter = reviewWriter;
    this.documentExecutor = tabularReviewCellExecutor;
  }

  @Async("tabularReviewExecutor")
  public void run(UUID reviewId) {
    TabularReview review = reviewWriter.requireReview(reviewId);
    if (review == null) {
      log.warn("Tabular review {} vanished before the run started", reviewId);
      return;
    }

    reviewWriter.markReviewStatus(reviewId, TabularReviewStatus.RUNNING, null);
    List<TabularReviewDocument> documents =
        reviewDocumentRepository.findByReviewIdOrderByPositionAsc(reviewId);

    try {
      long succeeded =
          documents.stream()
              .map(
                  document ->
                      CompletableFuture.supplyAsync(
                          () ->
                              documentProcessor.process(
                                  reviewId,
                                  document.getDocumentId(),
                                  document.getDocumentTitle(),
                                  review.getQuestions(),
                                  review.getLawyerId()),
                          documentExecutor))
              .toList()
              .stream()
              .filter(CompletableFuture::join)
              .count();

      reviewWriter.markReviewStatus(
          reviewId,
          outcome(succeeded, documents.size()),
          succeeded == 0 ? "Ни один документ не удалось разобрать" : null);
      log.info(
          "Tabular review {} finished: {}/{} document(s) processed",
          reviewId,
          succeeded,
          documents.size());
    } catch (RuntimeException e) {
      log.error("Tabular review {} run failed: {}", reviewId, e.getMessage(), e);
      reviewWriter.markReviewStatus(reviewId, TabularReviewStatus.FAILED, e.getMessage());
    }
  }

  private TabularReviewStatus outcome(long succeeded, int total) {
    if (succeeded == total) {
      return TabularReviewStatus.COMPLETED;
    }
    return succeeded == 0 ? TabularReviewStatus.FAILED : TabularReviewStatus.PARTIAL;
  }
}
