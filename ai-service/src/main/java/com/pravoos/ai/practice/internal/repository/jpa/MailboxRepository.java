package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailboxRepository extends JpaRepository<Mailbox, UUID> {

  List<Mailbox> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<Mailbox> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByUserIdAndEmailAddress(UUID userId, String emailAddress);

  long countByUserId(UUID userId);

  List<Mailbox> findBySyncEnabledTrue();
}
