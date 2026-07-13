package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CaseMessageRepository extends JpaRepository<CaseMessage, UUID> {

    interface ThreadView {
        UUID getCaseId();
        String getCaseTitle();
        String getClientName();
        String getLastBody();
        LocalDateTime getLastCreatedAt();
        String getLastAuthorRole();
        long getUnreadCount();
    }

    List<CaseMessage> findByCaseIdOrderByCreatedAtAsc(UUID caseId);

    @Query(value = """
            SELECT * FROM (
                SELECT DISTINCT ON (m.case_id)
                    m.case_id                      AS "caseId",
                    c.title                        AS "caseTitle",
                    cl.name                        AS "clientName",
                    m.body                         AS "lastBody",
                    m.created_at                   AS "lastCreatedAt",
                    m.author_role                  AS "lastAuthorRole",
                    (SELECT COUNT(*) FROM case_messages unread
                      WHERE unread.case_id = m.case_id
                        AND unread.author_role = 'CLIENT'
                        AND (r.last_read_at IS NULL OR unread.created_at > r.last_read_at)) AS "unreadCount"
                FROM case_messages m
                JOIN cases c ON c.id = m.case_id
                LEFT JOIN clients cl ON cl.id = c.client_id
                LEFT JOIN case_thread_reads r ON r.case_id = m.case_id AND r.user_id = :lawyerId
                WHERE c.lawyer_id = :lawyerId
                ORDER BY m.case_id, m.created_at DESC
            ) threads
            ORDER BY threads."lastCreatedAt" DESC
            """, nativeQuery = true)
    List<ThreadView> findLawyerThreads(@Param("lawyerId") UUID lawyerId);
}
