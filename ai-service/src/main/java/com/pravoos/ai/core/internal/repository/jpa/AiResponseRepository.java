package com.pravoos.ai.core.internal.repository.jpa;

import com.pravoos.ai.core.internal.model.entity.AiResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AiResponseRepository extends JpaRepository<AiResponse, UUID> {

    List<AiResponse> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

    List<AiResponse> findTop50ByOrderByCreatedAtDesc();

    long countByWorkflowId(String workflowId);

    @Query("SELECT r.workflowId, COUNT(r), AVG(r.rating) FROM AiResponse r GROUP BY r.workflowId")
    List<Object[]> aggregateByWorkflow();

    @Query("SELECT COUNT(r) FROM AiResponse r WHERE r.rating IS NOT NULL")
    long countRated();

    @Query("SELECT COUNT(r) FROM AiResponse r WHERE r.rating = 1")
    long countPositive();

    @Query("SELECT COUNT(r) FROM AiResponse r WHERE r.rating = -1")
    long countNegative();
}
