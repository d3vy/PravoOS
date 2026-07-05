package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CaseTaskRepository extends JpaRepository<CaseTask, UUID> {

    List<CaseTask> findByCaseIdOrderByDoneAscCreatedAtAsc(UUID caseId);

    @Query("SELECT COUNT(t) FROM CaseTask t WHERE t.done = false "
            + "AND t.caseId IN (SELECT c.id FROM Case c WHERE c.lawyerId = :lawyerId)")
    long countOpenByLawyerId(@Param("lawyerId") UUID lawyerId);

    @Modifying
    @Query("DELETE FROM CaseTask t WHERE t.caseId IN (SELECT c.id FROM Case c WHERE c.lawyerId = :lawyerId)")
    int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);
}
