package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.recyclebin.api.LeafSoftDeleteRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailboxRepository extends LeafSoftDeleteRepository<Mailbox> {

  List<Mailbox> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<Mailbox> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByUserIdAndEmailAddress(UUID userId, String emailAddress);

  long countByUserId(UUID userId);

  @Query(
      "SELECT m.id FROM Mailbox m WHERE m.syncEnabled = TRUE "
          + "AND (m.nextAttemptAt IS NULL OR m.nextAttemptAt <= :now)")
  Page<UUID> findIdsDueForSync(@Param("now") LocalDateTime now, Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value = "UPDATE mailboxes SET deleted_at = :deletedAt WHERE id = :id AND deleted_at IS NULL",
      nativeQuery = true)
  int softDelete(@Param("id") UUID id, @Param("deletedAt") LocalDateTime deletedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "UPDATE mailboxes SET deleted_at = NULL WHERE id = :id", nativeQuery = true)
  int restore(@Param("id") UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM mailboxes WHERE id = :id", nativeQuery = true)
  int hardDelete(@Param("id") UUID id);
}
