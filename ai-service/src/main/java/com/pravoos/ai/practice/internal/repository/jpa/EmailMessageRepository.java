package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, UUID> {

  boolean existsByMailboxIdAndMessageId(UUID mailboxId, String messageId);

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

  @Query(
      "SELECT m FROM EmailMessage m WHERE m.mailboxId = :mailboxId "
          + "AND m.caseId IS NULL AND m.clientId IS NULL "
          + "AND m.autoLinkAttempts < :maxAttempts "
          + "ORDER BY m.sentAt ASC, m.createdAt ASC")
  List<EmailMessage> findAutoLinkCandidates(
      @Param("mailboxId") UUID mailboxId, @Param("maxAttempts") int maxAttempts, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT m FROM EmailMessage m WHERE m.id = :messageId AND m.mailboxId IN "
          + "(SELECT b.id FROM Mailbox b WHERE b.userId = :userId)")
  Optional<EmailMessage> findByIdAndUserIdForUpdate(
      @Param("messageId") UUID messageId, @Param("userId") UUID userId);
}
