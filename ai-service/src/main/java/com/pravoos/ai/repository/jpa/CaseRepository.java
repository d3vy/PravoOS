package com.pravoos.ai.repository.jpa;

import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.model.enums.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID> {

    List<Case> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

    List<Case> findByLawyerIdAndStatusOrderByCreatedAtDesc(UUID lawyerId, CaseStatus status);

    List<Case> findByClientIdAndLawyerIdOrderByCreatedAtDesc(UUID clientId, UUID lawyerId);

    List<Case> findByFilingDeadline(LocalDate filingDeadline);

    List<Case> findByNextHearingDate(LocalDate nextHearingDate);

    List<Case> findByExpiresAt(LocalDate expiresAt);

    @Query("""
            SELECT c FROM Case c
            WHERE c.lawyerId = :lawyerId
              AND (:status IS NULL OR c.status = :status)
              AND (LOWER(c.title) LIKE :pattern ESCAPE '\\'
                   OR LOWER(c.description) LIKE :pattern ESCAPE '\\'
                   OR EXISTS (SELECT 1 FROM Client cl
                              WHERE cl.id = c.clientId AND LOWER(cl.name) LIKE :pattern ESCAPE '\\'))
            ORDER BY c.createdAt DESC
            """)
    List<Case> search(@Param("lawyerId") UUID lawyerId,
                      @Param("status") CaseStatus status,
                      @Param("pattern") String pattern);

    int deleteByLawyerId(UUID lawyerId);
}
