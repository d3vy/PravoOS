package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.InvoicePayment;
import com.pravoos.ai.shared.model.enums.InvoicePaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID> {

  Optional<InvoicePayment> findByProviderPaymentId(String providerPaymentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM InvoicePayment p WHERE p.providerPaymentId = :providerPaymentId")
  Optional<InvoicePayment> findByProviderPaymentIdForUpdate(
      @Param("providerPaymentId") String providerPaymentId);

  List<InvoicePayment> findByInvoiceIdAndStatusOrderByCreatedAtDesc(
      UUID invoiceId, InvoicePaymentStatus status);
}
