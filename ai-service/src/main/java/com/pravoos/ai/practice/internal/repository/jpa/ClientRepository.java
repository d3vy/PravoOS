package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.Client;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, UUID> {

  List<Client> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

  Page<Client> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId, Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM clients WHERE lawyer_id = :lawyerId", nativeQuery = true)
  int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value = "UPDATE clients SET deleted_at = :deletedAt WHERE id = :id AND deleted_at IS NULL",
      nativeQuery = true)
  int softDelete(@Param("id") UUID id, @Param("deletedAt") LocalDateTime deletedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "UPDATE clients SET deleted_at = NULL WHERE id = :id", nativeQuery = true)
  int restore(@Param("id") UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM clients WHERE id = :id", nativeQuery = true)
  int hardDelete(@Param("id") UUID id);
}
