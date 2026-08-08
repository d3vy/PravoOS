package com.pravoos.ai.core.internal.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.internal.model.entity.TabularReview;
import com.pravoos.ai.core.internal.model.entity.TabularReviewDocument;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewDocumentRepository;
import com.pravoos.ai.shared.model.enums.TabularReviewStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
class TabularReviewRunnerTest {

  @Mock private TabularReviewDocumentProcessor documentProcessor;
  @Mock private TabularReviewDocumentRepository reviewDocumentRepository;
  @Mock private TabularReviewWriter reviewWriter;

  private TabularReviewRunner runner;
  private ThreadPoolTaskExecutor executor;

  private final UUID reviewId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.initialize();
    runner =
        new TabularReviewRunner(
            documentProcessor, reviewDocumentRepository, reviewWriter, executor);
  }

  private TabularReview review() {
    TabularReview review = new TabularReview();
    review.setLawyerId(lawyerId);
    review.setQuestions(List.of("Кто стороны?"));
    return review;
  }

  private TabularReviewDocument reviewDocument(UUID documentId, String title) {
    TabularReviewDocument document = new TabularReviewDocument();
    document.setDocumentId(documentId);
    document.setDocumentTitle(title);
    return document;
  }

  @Test
  void runLogsAndReturnsWhenReviewVanishedBeforeStart() {
    when(reviewWriter.requireReview(reviewId)).thenReturn(null);

    runner.run(reviewId);

    verify(reviewWriter, never()).markReviewStatus(any(), any(), any());
    verify(reviewDocumentRepository, never()).findByReviewIdOrderByPositionAsc(any());
  }

  @Test
  void runMarksCompletedWhenAllDocumentsSucceed() {
    UUID documentId = UUID.randomUUID();
    when(reviewWriter.requireReview(reviewId)).thenReturn(review());
    when(reviewDocumentRepository.findByReviewIdOrderByPositionAsc(reviewId))
        .thenReturn(List.of(reviewDocument(documentId, "Договор")));
    when(documentProcessor.process(
            eq(reviewId), eq(documentId), eq("Договор"), anyList(), eq(lawyerId)))
        .thenReturn(true);

    runner.run(reviewId);

    verify(reviewWriter).markReviewStatus(reviewId, TabularReviewStatus.RUNNING, null);
    verify(reviewWriter).markReviewStatus(reviewId, TabularReviewStatus.COMPLETED, null);
  }

  @Test
  void runMarksPartialWhenSomeDocumentsFail() {
    UUID succeeded = UUID.randomUUID();
    UUID failed = UUID.randomUUID();
    when(reviewWriter.requireReview(reviewId)).thenReturn(review());
    when(reviewDocumentRepository.findByReviewIdOrderByPositionAsc(reviewId))
        .thenReturn(List.of(reviewDocument(succeeded, "A"), reviewDocument(failed, "B")));
    when(documentProcessor.process(eq(reviewId), eq(succeeded), eq("A"), anyList(), eq(lawyerId)))
        .thenReturn(true);
    when(documentProcessor.process(eq(reviewId), eq(failed), eq("B"), anyList(), eq(lawyerId)))
        .thenReturn(false);

    runner.run(reviewId);

    verify(reviewWriter).markReviewStatus(reviewId, TabularReviewStatus.PARTIAL, null);
  }

  @Test
  void runMarksFailedWithMessageWhenNoDocumentSucceeds() {
    UUID documentId = UUID.randomUUID();
    when(reviewWriter.requireReview(reviewId)).thenReturn(review());
    when(reviewDocumentRepository.findByReviewIdOrderByPositionAsc(reviewId))
        .thenReturn(List.of(reviewDocument(documentId, "Договор")));
    when(documentProcessor.process(
            eq(reviewId), eq(documentId), eq("Договор"), anyList(), eq(lawyerId)))
        .thenReturn(false);

    runner.run(reviewId);

    verify(reviewWriter)
        .markReviewStatus(
            reviewId, TabularReviewStatus.FAILED, "Ни один документ не удалось разобрать");
  }

  @Test
  void runPropagatesExceptionWhenLoadingDocumentsFailsBeforeTryBlock() {
    when(reviewWriter.requireReview(reviewId)).thenReturn(review());
    when(reviewDocumentRepository.findByReviewIdOrderByPositionAsc(reviewId))
        .thenThrow(new IllegalStateException("db down"));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> runner.run(reviewId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("db down");

    verify(reviewWriter).markReviewStatus(reviewId, TabularReviewStatus.RUNNING, null);
    verify(reviewWriter, never())
        .markReviewStatus(eq(reviewId), eq(TabularReviewStatus.FAILED), any());
  }

  @Test
  void runTreatsZeroQueuedDocumentsAsVacuouslyCompletedWithErrorNote() {
    when(reviewWriter.requireReview(reviewId)).thenReturn(review());
    when(reviewDocumentRepository.findByReviewIdOrderByPositionAsc(reviewId)).thenReturn(List.of());

    runner.run(reviewId);

    verify(reviewWriter)
        .markReviewStatus(
            reviewId, TabularReviewStatus.COMPLETED, "Ни один документ не удалось разобрать");
  }
}
