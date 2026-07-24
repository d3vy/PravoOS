package com.pravoos.ai.core.internal.repository.jpa;

import com.pravoos.ai.core.internal.model.entity.ContractReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractReviewRepository extends JpaRepository<ContractReview, UUID> {

  List<ContractReview> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

  List<ContractReview> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
