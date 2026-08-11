package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailboxRepository extends JpaRepository<Mailbox, UUID> {

  List<Mailbox> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<Mailbox> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByUserIdAndEmailAddress(UUID userId, String emailAddress);

  long countByUserId(UUID userId);

  @Query(
      "SELECT m.id FROM Mailbox m WHERE m.syncEnabled = TRUE "
          + "AND (m.nextAttemptAt IS NULL OR m.nextAttemptAt <= :now)")
  List<UUID> findIdsDueForSync(@Param("now") LocalDateTime now);
}
