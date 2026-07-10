package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.WorkflowRun;
import com.pravoos.ai.shared.model.enums.WorkflowRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {

    List<WorkflowRun> findByCaseIdOrderByStartedAtDesc(UUID caseId);

    List<WorkflowRun> findByStatusAndStartedAtBefore(WorkflowRunStatus status, LocalDateTime threshold);

    @Modifying
    @Query("DELETE FROM WorkflowRun r WHERE r.caseId IN "
            + "(SELECT c.id FROM Case c WHERE c.lawyerId = :lawyerId)")
    int deleteByLawyerCases(@Param("lawyerId") UUID lawyerId);
}
