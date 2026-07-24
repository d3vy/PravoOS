package com.pravoos.ai.core.internal.repository.jpa;

import com.pravoos.ai.core.internal.model.entity.TabularReviewCell;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TabularReviewCellRepository extends JpaRepository<TabularReviewCell, UUID> {

  List<TabularReviewCell> findByReviewId(UUID reviewId);

  void deleteByReviewIdAndDocumentId(UUID reviewId, UUID documentId);
}
