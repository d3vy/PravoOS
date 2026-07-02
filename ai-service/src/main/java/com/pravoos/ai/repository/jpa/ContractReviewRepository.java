package com.pravoos.ai.repository.jpa;

import com.pravoos.ai.model.entity.ContractReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContractReviewRepository extends JpaRepository<ContractReview, UUID> {

    List<ContractReview> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

    List<ContractReview> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
