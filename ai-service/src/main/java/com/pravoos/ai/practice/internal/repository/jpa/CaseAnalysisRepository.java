package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CaseAnalysisRepository extends JpaRepository<CaseAnalysis, UUID> {

    Optional<CaseAnalysis> findByCaseId(UUID caseId);

    @Modifying
    @Query("DELETE FROM CaseAnalysis a WHERE a.caseId IN "
            + "(SELECT c.id FROM Case c WHERE c.lawyerId = :lawyerId)")
    int deleteByLawyerCases(@Param("lawyerId") UUID lawyerId);
}
