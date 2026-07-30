package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import java.time.LocalDate;
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

  List<Invoice> findByLawyerIdAndStatusOrderByDueDateAsc(UUID lawyerId, InvoiceStatus status);

  List<Invoice> findByStatusAndDueDate(InvoiceStatus status, LocalDate dueDate);

  @Query("SELECT DISTINCT i.lawyerId FROM Invoice i WHERE i.status = :status")
  List<UUID> findDistinctLawyerIdsByStatus(@Param("status") InvoiceStatus status);

  Optional<Invoice> findByIdAndClientIdIn(UUID id, List<UUID> clientIds);

  List<Invoice> findByClientIdInOrderByCreatedAtDesc(List<UUID> clientIds);

  @Modifying
  @Query("DELETE FROM Invoice i WHERE i.lawyerId = :lawyerId")
  int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);
}
