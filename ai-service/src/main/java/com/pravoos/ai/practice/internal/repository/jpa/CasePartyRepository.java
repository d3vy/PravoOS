package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseParty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CasePartyRepository extends JpaRepository<CaseParty, UUID> {

  interface PartyLookup {
    UUID getCaseId();

    String getCaseTitle();

    String getPartyName();

    String getPartyRole();
  }

  List<CaseParty> findByCaseId(UUID caseId);

  @Query(
      value =
          """
            SELECT c.id            AS "caseId",
                   c.title         AS "caseTitle",
                   p.name          AS "partyName",
                   p.role          AS "partyRole"
            FROM case_parties p
                     JOIN cases c ON c.id = p.case_id
            WHERE c.lawyer_id = :lawyerId
              AND length(btrim(regexp_replace(lower(p.name), '\\s+', ' ', 'g'))) >= :minLength
              AND (strpos(btrim(regexp_replace(lower(p.name), '\\s+', ' ', 'g')), :query) > 0
                OR strpos(:query, btrim(regexp_replace(lower(p.name), '\\s+', ' ', 'g'))) > 0)
            ORDER BY c.title, p.name
            LIMIT :maxHits
            """,
      nativeQuery = true)
  List<PartyLookup> searchConflicts(
      @Param("lawyerId") UUID lawyerId,
      @Param("query") String normalizedQuery,
      @Param("minLength") int minLength,
      @Param("maxHits") int maxHits);

  @Modifying
  void deleteByCaseId(UUID caseId);

  @Modifying
  @Query(
      "DELETE FROM CaseParty p WHERE p.caseId IN "
          + "(SELECT c.id FROM Case c WHERE c.lawyerId = :lawyerId)")
  int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);
}
