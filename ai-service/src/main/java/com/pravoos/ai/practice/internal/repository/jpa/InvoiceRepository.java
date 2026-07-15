package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId, Pageable pageable);

    Page<Invoice> findByLawyerIdAndClientIdOrderByCreatedAtDesc(UUID lawyerId, UUID clientId, Pageable pageable);

    Optional<Invoice> findByIdAndLawyerId(UUID id, UUID lawyerId);

    long countByLawyerIdAndNumberStartingWith(UUID lawyerId, String prefix);

    @Modifying
    @Query("DELETE FROM Invoice i WHERE i.lawyerId = :lawyerId")
    int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);
}
