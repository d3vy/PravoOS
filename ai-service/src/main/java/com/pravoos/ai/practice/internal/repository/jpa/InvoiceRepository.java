package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId, Pageable pageable);

    Page<Invoice> findByLawyerIdAndClientIdOrderByCreatedAtDesc(UUID lawyerId, UUID clientId, Pageable pageable);

    Optional<Invoice> findByIdAndLawyerId(UUID id, UUID lawyerId);

    long countByLawyerIdAndNumberStartingWith(UUID lawyerId, String prefix);

    List<Invoice> findByLawyerIdAndStatusOrderByDueDateAsc(UUID lawyerId, InvoiceStatus status);

    @Modifying
    @Query("DELETE FROM Invoice i WHERE i.lawyerId = :lawyerId")
    int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);
}
