package com.pravoos.ai.core.internal.repository.jpa;

import com.pravoos.ai.core.internal.model.entity.TabularReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TabularReviewRepository extends JpaRepository<TabularReview, UUID> {

  List<TabularReview> findByCaseIdOrderByCreatedAtDesc(UUID caseId);
}
