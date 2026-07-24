package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseParty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CasePartyRepository extends JpaRepository<CaseParty, UUID> {

  List<CaseParty> findByCaseId(UUID caseId);

  @Modifying
  void deleteByCaseId(UUID caseId);

  @Modifying
  @Query(
      "DELETE FROM CaseParty p WHERE p.caseId IN "
          + "(SELECT c.id FROM Case c WHERE c.lawyerId = :lawyerId)")
  int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);
}
