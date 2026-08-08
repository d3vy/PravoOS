package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, UUID> {

  @Query(
      "SELECT m.messageId FROM EmailMessage m "
          + "WHERE m.mailboxId = :mailboxId AND m.messageId IN :messageIds")
  Set<String> findExistingMessageIds(
      @Param("mailboxId") UUID mailboxId, @Param("messageIds") Collection<String> messageIds);

  List<EmailMessage> findByCaseIdOrderBySentAtDesc(UUID caseId);

  List<EmailMessage> findByClientIdOrderBySentAtDesc(UUID clientId);

  long countByMailboxId(UUID mailboxId);

  @Query(
      "SELECT m FROM EmailMessage m WHERE m.id = :messageId AND m.mailboxId IN "
          + "(SELECT b.id FROM Mailbox b WHERE b.userId = :userId)")
  Optional<EmailMessage> findByIdAndUserId(
      @Param("messageId") UUID messageId, @Param("userId") UUID userId);

  @Query(
      "SELECT m FROM EmailMessage m WHERE m.caseId IS NULL AND m.clientId IS NULL "
          + "AND m.mailboxId IN (SELECT b.id FROM Mailbox b WHERE b.userId = :userId) "
          + "ORDER BY m.sentAt DESC, m.createdAt DESC")
  Page<EmailMessage> findUnlinkedByUserId(@Param("userId") UUID userId, Pageable pageable);

  @Query(
      "SELECT m FROM EmailMessage m WHERE m.mailboxId = :mailboxId AND m.threadKey = :threadKey "
          + "AND (m.caseId IS NOT NULL OR m.clientId IS NOT NULL) "
          + "ORDER BY m.linkedAt DESC")
  List<EmailMessage> findLinkedInThread(
      @Param("mailboxId") UUID mailboxId, @Param("threadKey") String threadKey, Pageable pageable);

  List<EmailMessage> findByMailboxIdAndCaseIdIsNullAndClientIdIsNullOrderBySentAtAsc(
      UUID mailboxId);
}
