package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID> {

    interface StatusCountView {
        CaseStatus getStatus();
        long getCount();
    }

    interface CourtStatView {
        String getCourtName();
        long getTotalCases();
        long getWonCases();
        long getLostCases();
    }

    @Query("""
            SELECT h.courtName AS courtName,
                   COUNT(DISTINCT c.id) AS totalCases,
                   COUNT(DISTINCT CASE WHEN c.status = :wonStatus THEN c.id END) AS wonCases,
                   COUNT(DISTINCT CASE WHEN c.status = :lostStatus THEN c.id END) AS lostCases
            FROM CaseHearingEvent h, Case c
            WHERE c.id = h.caseId
              AND h.courtName IN :courtNames
              AND (c.lawyerId = :lawyerId OR c.orgId IN :orgIds)
            GROUP BY h.courtName
            """)
    List<CourtStatView> courtStatistics(@Param("courtNames") Collection<String> courtNames,
                                        @Param("lawyerId") UUID lawyerId,
                                        @Param("orgIds") Collection<UUID> orgIds,
                                        @Param("wonStatus") CaseStatus wonStatus,
                                        @Param("lostStatus") CaseStatus lostStatus);

    @Query("SELECT c.status AS status, COUNT(c) AS count FROM Case c "
            + "WHERE c.lawyerId = :lawyerId GROUP BY c.status")
    List<StatusCountView> countGroupedByStatus(@Param("lawyerId") UUID lawyerId);

    long countByLawyerIdAndStatusNotIn(UUID lawyerId, Collection<CaseStatus> statuses);

    @Query("""
            SELECT c FROM Case c
            WHERE c.lawyerId = :lawyerId
              AND c.status NOT IN :closedStatuses
              AND ((c.filingDeadline BETWEEN :today AND :horizon)
                   OR (c.nextHearingDate BETWEEN :today AND :horizon)
                   OR (c.expiresAt BETWEEN :today AND :horizon))
            """)
    List<Case> findCasesWithUpcomingDeadlines(@Param("lawyerId") UUID lawyerId,
                                              @Param("closedStatuses") Collection<CaseStatus> closedStatuses,
                                              @Param("today") LocalDate today,
                                              @Param("horizon") LocalDate horizon);

    List<Case> findTop5ByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

    List<Case> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

    Page<Case> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId, Pageable pageable);

    List<Case> findByLawyerIdAndStatusOrderByCreatedAtDesc(UUID lawyerId, CaseStatus status);

    Page<Case> findByLawyerIdAndStatusOrderByCreatedAtDesc(UUID lawyerId, CaseStatus status, Pageable pageable);

    List<Case> findByClientIdAndLawyerIdOrderByCreatedAtDesc(UUID clientId, UUID lawyerId);

    List<Case> findByClientIdInOrderByCreatedAtDesc(Collection<UUID> clientIds);

    List<Case> findByFilingDeadline(LocalDate filingDeadline);

    List<Case> findByNextHearingDate(LocalDate nextHearingDate);

    List<Case> findByExpiresAt(LocalDate expiresAt);

    List<Case> findByArbitrCaseNumberIsNotNull();

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

    @Query(value = """
            SELECT c FROM Case c
            WHERE c.lawyerId = :lawyerId
              AND (:status IS NULL OR c.status = :status)
              AND (LOWER(c.title) LIKE :pattern ESCAPE '\\'
                   OR LOWER(c.description) LIKE :pattern ESCAPE '\\'
                   OR EXISTS (SELECT 1 FROM Client cl
                              WHERE cl.id = c.clientId AND LOWER(cl.name) LIKE :pattern ESCAPE '\\'))
            ORDER BY c.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(c) FROM Case c
            WHERE c.lawyerId = :lawyerId
              AND (:status IS NULL OR c.status = :status)
              AND (LOWER(c.title) LIKE :pattern ESCAPE '\\'
                   OR LOWER(c.description) LIKE :pattern ESCAPE '\\'
                   OR EXISTS (SELECT 1 FROM Client cl
                              WHERE cl.id = c.clientId AND LOWER(cl.name) LIKE :pattern ESCAPE '\\'))
            """)
    Page<Case> search(@Param("lawyerId") UUID lawyerId,
                      @Param("status") CaseStatus status,
                      @Param("pattern") String pattern,
                      Pageable pageable);

    @Query(value = """
            SELECT c FROM Case c
            WHERE (c.lawyerId = :lawyerId OR c.orgId IN :orgIds)
              AND (:status IS NULL OR c.status = :status)
              AND (:orgFilter IS NULL OR c.orgId = :orgFilter)
              AND (:pattern IS NULL
                   OR LOWER(c.title) LIKE :pattern ESCAPE '\\'
                   OR LOWER(c.description) LIKE :pattern ESCAPE '\\'
                   OR EXISTS (SELECT 1 FROM Client cl
                              WHERE cl.id = c.clientId AND LOWER(cl.name) LIKE :pattern ESCAPE '\\'))
            ORDER BY c.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(c) FROM Case c
            WHERE (c.lawyerId = :lawyerId OR c.orgId IN :orgIds)
              AND (:status IS NULL OR c.status = :status)
              AND (:orgFilter IS NULL OR c.orgId = :orgFilter)
              AND (:pattern IS NULL
                   OR LOWER(c.title) LIKE :pattern ESCAPE '\\'
                   OR LOWER(c.description) LIKE :pattern ESCAPE '\\'
                   OR EXISTS (SELECT 1 FROM Client cl
                              WHERE cl.id = c.clientId AND LOWER(cl.name) LIKE :pattern ESCAPE '\\'))
            """)
    Page<Case> findVisible(@Param("lawyerId") UUID lawyerId,
                           @Param("orgIds") Collection<UUID> orgIds,
                           @Param("status") CaseStatus status,
                           @Param("orgFilter") UUID orgFilter,
                           @Param("pattern") String pattern,
                           Pageable pageable);

    @Query("""
            SELECT c FROM Case c
            WHERE (c.lawyerId = :lawyerId OR c.orgId IN :orgIds)
              AND (:caseId IS NULL OR c.id = :caseId)
              AND (:clientId IS NULL OR c.clientId = :clientId)
            """)
    List<Case> findVisibleForCalendar(@Param("lawyerId") UUID lawyerId,
                                      @Param("orgIds") Collection<UUID> orgIds,
                                      @Param("caseId") UUID caseId,
                                      @Param("clientId") UUID clientId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Case c SET c.lawyerId = :newOwnerId, c.orgId = null "
            + "WHERE c.lawyerId = :lawyerId AND c.orgId = :orgId")
    int reassignOrgCasesToOwner(@Param("lawyerId") UUID lawyerId,
                                @Param("orgId") UUID orgId,
                                @Param("newOwnerId") UUID newOwnerId);

    int deleteByLawyerId(UUID lawyerId);
}
