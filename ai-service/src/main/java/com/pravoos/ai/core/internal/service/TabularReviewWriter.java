package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.internal.model.entity.TabularReview;
import com.pravoos.ai.core.internal.model.entity.TabularReviewCell;
import com.pravoos.ai.core.internal.model.entity.TabularReviewDocument;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewCellRepository;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewDocumentRepository;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewRepository;
import com.pravoos.ai.shared.model.enums.TabularReviewStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class TabularReviewWriter {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;

    private final TabularReviewRepository reviewRepository;
    private final TabularReviewDocumentRepository reviewDocumentRepository;
    private final TabularReviewCellRepository cellRepository;

    public TabularReviewWriter(TabularReviewRepository reviewRepository,
                               TabularReviewDocumentRepository reviewDocumentRepository,
                               TabularReviewCellRepository cellRepository) {
        this.reviewRepository = reviewRepository;
        this.reviewDocumentRepository = reviewDocumentRepository;
        this.cellRepository = cellRepository;
    }

    @Transactional
    public void markReviewStatus(UUID reviewId, TabularReviewStatus status, String errorMessage) {
        reviewRepository.findById(reviewId).ifPresent(review -> {
            review.setStatus(status);
            review.setErrorMessage(truncate(errorMessage));
            if (status.terminal()) {
                review.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
            }
            reviewRepository.save(review);
        });
    }

    @Transactional
    public void saveDocumentResult(UUID reviewId, UUID documentId, List<TabularReviewCell> cells) {
        cellRepository.deleteByReviewIdAndDocumentId(reviewId, documentId);
        cellRepository.saveAll(cells);
        markDocumentStatus(reviewId, documentId, TabularReviewStatus.COMPLETED, null);
    }

    @Transactional
    public void markDocumentFailed(UUID reviewId, UUID documentId, String errorMessage) {
        markDocumentStatus(reviewId, documentId, TabularReviewStatus.FAILED, errorMessage);
    }

    @Transactional
    public void markDocumentRunning(UUID reviewId, UUID documentId) {
        markDocumentStatus(reviewId, documentId, TabularReviewStatus.RUNNING, null);
    }

    private void markDocumentStatus(UUID reviewId, UUID documentId,
                                    TabularReviewStatus status, String errorMessage) {
        TabularReviewDocument document = reviewDocumentRepository
                .findByReviewIdAndDocumentId(reviewId, documentId)
                .orElse(null);
        if (document == null) {
            return;
        }
        document.setStatus(status);
        document.setErrorMessage(truncate(errorMessage));
        reviewDocumentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public TabularReview requireReview(UUID reviewId) {
        return reviewRepository.findById(reviewId).orElse(null);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= ERROR_MESSAGE_MAX_LENGTH
                ? value
                : value.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }
}
