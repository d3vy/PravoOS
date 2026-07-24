package com.pravoos.ai.core.internal.repository.jpa;

import com.pravoos.ai.core.internal.model.entity.TabularReviewDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TabularReviewDocumentRepository
    extends JpaRepository<TabularReviewDocument, UUID> {

  List<TabularReviewDocument> findByReviewIdOrderByPositionAsc(UUID reviewId);

  Optional<TabularReviewDocument> findByReviewIdAndDocumentId(UUID reviewId, UUID documentId);
}
