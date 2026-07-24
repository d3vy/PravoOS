package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseDraft;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseDraftRepository extends JpaRepository<CaseDraft, UUID> {

  List<CaseDraft> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

  Optional<CaseDraft> findByIdAndLawyerId(UUID id, UUID lawyerId);

  int deleteByLawyerId(UUID lawyerId);

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE CaseDraft d SET d.lawyerId = :newOwnerId "
          + "WHERE d.lawyerId = :lawyerId "
          + "AND d.caseId IN (SELECT c.id FROM Case c WHERE c.lawyerId = :lawyerId AND c.orgId = :orgId)")
  int reassignForOrgCases(
      @Param("lawyerId") UUID lawyerId,
      @Param("orgId") UUID orgId,
      @Param("newOwnerId") UUID newOwnerId);
}
