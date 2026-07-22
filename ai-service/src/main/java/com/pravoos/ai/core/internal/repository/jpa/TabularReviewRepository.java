package com.pravoos.ai.core.internal.repository.jpa;

import com.pravoos.ai.core.internal.model.entity.TabularReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TabularReviewRepository extends JpaRepository<TabularReview, UUID> {

    List<TabularReview> findByCaseIdOrderByCreatedAtDesc(UUID caseId);
}
