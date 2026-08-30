package com.pravoos.user.shared.repository;

import com.pravoos.user.shared.model.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query(
      "SELECT e FROM OutboxEvent e WHERE e.publishedAt IS NULL AND e.attempts < :maxAttempts "
          + "ORDER BY e.createdAt ASC")
  List<OutboxEvent> lockUnpublishedBatch(@Param("maxAttempts") int maxAttempts, Pageable pageable);

  @Query(
      "SELECT COUNT(e) FROM OutboxEvent e WHERE e.publishedAt IS NULL AND e.attempts >= :maxAttempts")
  long countParked(@Param("maxAttempts") int maxAttempts);
}
