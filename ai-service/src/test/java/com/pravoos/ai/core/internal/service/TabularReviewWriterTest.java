package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.internal.model.entity.TabularReview;
import com.pravoos.ai.core.internal.model.entity.TabularReviewCell;
import com.pravoos.ai.core.internal.model.entity.TabularReviewDocument;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewCellRepository;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewDocumentRepository;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewRepository;
import com.pravoos.ai.shared.model.enums.TabularReviewStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TabularReviewWriterTest {

  @Mock private TabularReviewRepository reviewRepository;
  @Mock private TabularReviewDocumentRepository reviewDocumentRepository;
  @Mock private TabularReviewCellRepository cellRepository;

  private TabularReviewWriter writer;

  private final UUID reviewId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    writer = new TabularReviewWriter(reviewRepository, reviewDocumentRepository, cellRepository);
  }

  @Test
  void markReviewStatusIsNoOpWhenReviewVanished() {
    when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());

    writer.markReviewStatus(reviewId, TabularReviewStatus.FAILED, "error");

    verify(reviewRepository, never()).save(any());
  }

  @Test
  void markReviewStatusUpdatesStatusAndErrorMessage() {
    TabularReview review = new TabularReview();
    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

    writer.markReviewStatus(reviewId, TabularReviewStatus.RUNNING, null);

    assertThat(review.getStatus()).isEqualTo(TabularReviewStatus.RUNNING);
    assertThat(review.getErrorMessage()).isNull();
    assertThat(review.getCompletedAt()).isNull();
    verify(reviewRepository).save(review);
  }

  @Test
  void markReviewStatusSetsCompletedAtForTerminalStatuses() {
    TabularReview review = new TabularReview();
    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

    writer.markReviewStatus(reviewId, TabularReviewStatus.COMPLETED, null);

    assertThat(review.getCompletedAt()).isNotNull();
  }

  @Test
  void markReviewStatusTruncatesLongErrorMessage() {
    TabularReview review = new TabularReview();
    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
    String longMessage = "e".repeat(600);

    writer.markReviewStatus(reviewId, TabularReviewStatus.FAILED, longMessage);

    assertThat(review.getErrorMessage()).hasSize(500);
  }

  @Test
  void saveDocumentResultReplacesCellsAndMarksDocumentCompleted() {
    TabularReviewCell cell = new TabularReviewCell();
    TabularReviewDocument document = new TabularReviewDocument();
    when(reviewDocumentRepository.findByReviewIdAndDocumentId(reviewId, documentId))
        .thenReturn(Optional.of(document));

    writer.saveDocumentResult(reviewId, documentId, List.of(cell));

    verify(cellRepository).deleteByReviewIdAndDocumentId(reviewId, documentId);
    ArgumentCaptor<List<TabularReviewCell>> captor = ArgumentCaptor.forClass(List.class);
    verify(cellRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).containsExactly(cell);
    assertThat(document.getStatus()).isEqualTo(TabularReviewStatus.COMPLETED);
  }

  @Test
  void markDocumentFailedIsNoOpWhenDocumentVanished() {
    when(reviewDocumentRepository.findByReviewIdAndDocumentId(reviewId, documentId))
        .thenReturn(Optional.empty());

    writer.markDocumentFailed(reviewId, documentId, "boom");

    verify(reviewDocumentRepository, never()).save(any());
  }

  @Test
  void markDocumentFailedSetsFailedStatusAndErrorMessage() {
    TabularReviewDocument document = new TabularReviewDocument();
    when(reviewDocumentRepository.findByReviewIdAndDocumentId(reviewId, documentId))
        .thenReturn(Optional.of(document));

    writer.markDocumentFailed(reviewId, documentId, "boom");

    assertThat(document.getStatus()).isEqualTo(TabularReviewStatus.FAILED);
    assertThat(document.getErrorMessage()).isEqualTo("boom");
    verify(reviewDocumentRepository).save(document);
  }

  @Test
  void markDocumentRunningSetsRunningStatus() {
    TabularReviewDocument document = new TabularReviewDocument();
    when(reviewDocumentRepository.findByReviewIdAndDocumentId(reviewId, documentId))
        .thenReturn(Optional.of(document));

    writer.markDocumentRunning(reviewId, documentId);

    assertThat(document.getStatus()).isEqualTo(TabularReviewStatus.RUNNING);
  }

  @Test
  void requireReviewReturnsNullWhenMissing() {
    when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());

    assertThat(writer.requireReview(reviewId)).isNull();
  }

  @Test
  void requireReviewReturnsExistingReview() {
    TabularReview review = new TabularReview();
    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

    assertThat(writer.requireReview(reviewId)).isSameAs(review);
  }
}
