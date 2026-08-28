package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

  Page<Invoice> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId, Pageable pageable);

  Page<Invoice> findByLawyerIdAndClientIdOrderByCreatedAtDesc(
      UUID lawyerId, UUID clientId, Pageable pageable);

  Optional<Invoice> findByIdAndLawyerId(UUID id, UUID lawyerId);

  long countByLawyerIdAndNumberStartingWith(UUID lawyerId, String prefix);

  @Query(
      value =
          "SELECT COALESCE(MAX(CAST(SUBSTRING(number FROM :sequenceStart) AS BIGINT)), 0) "
              + "FROM invoices "
              + "WHERE lawyer_id = :lawyerId AND number ~ :numberPattern",
      nativeQuery = true)
  long findMaxNumberSequence(
      @Param("lawyerId") UUID lawyerId,
      @Param("numberPattern") String numberPattern,
      @Param("sequenceStart") int sequenceStart);

  List<Invoice> findByLawyerIdAndStatusOrderByDueDateAsc(UUID lawyerId, InvoiceStatus status);

  List<Invoice> findByStatusAndDueDate(InvoiceStatus status, LocalDate dueDate);

  @Query(
      """
            SELECT i FROM Invoice i
            WHERE i.lawyerId = :lawyerId
              AND (LOWER(i.number) LIKE :pattern ESCAPE '!' OR i.clientId IN :clientIds)
            ORDER BY i.createdAt DESC
            """)
  List<Invoice> search(
      @Param("lawyerId") UUID lawyerId,
      @Param("pattern") String pattern,
      @Param("clientIds") Collection<UUID> clientIds,
      Pageable pageable);

  @Query("SELECT DISTINCT i.lawyerId FROM Invoice i WHERE i.status = :status")
  List<UUID> findDistinctLawyerIdsByStatus(@Param("status") InvoiceStatus status);

  long countByClientIdAndStatusNot(UUID clientId, InvoiceStatus status);

  Optional<Invoice> findByIdAndClientIdIn(UUID id, List<UUID> clientIds);

  List<Invoice> findByClientIdInOrderByCreatedAtDesc(List<UUID> clientIds);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM invoices WHERE lawyer_id = :lawyerId", nativeQuery = true)
  int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value = "UPDATE invoices SET deleted_at = :deletedAt WHERE id = :id AND deleted_at IS NULL",
      nativeQuery = true)
  int softDelete(@Param("id") UUID id, @Param("deletedAt") LocalDateTime deletedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "UPDATE invoices SET deleted_at = NULL WHERE id = :id", nativeQuery = true)
  int restore(@Param("id") UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM invoices WHERE id = :id", nativeQuery = true)
  int hardDelete(@Param("id") UUID id);
}
