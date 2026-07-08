package com.pravoos.ai.core.internal.repository.jpa;

import com.pravoos.ai.core.internal.model.entity.DocumentComparison;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentComparisonRepository extends JpaRepository<DocumentComparison, UUID> {

    List<DocumentComparison> findByCaseIdOrderByCreatedAtDesc(UUID caseId);
}
