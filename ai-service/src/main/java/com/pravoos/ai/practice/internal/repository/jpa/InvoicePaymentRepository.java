package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.InvoicePayment;
import com.pravoos.ai.shared.model.enums.InvoicePaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID> {

  Optional<InvoicePayment> findByProviderPaymentId(String providerPaymentId);

  List<InvoicePayment> findByInvoiceIdAndStatusOrderByCreatedAtDesc(
      UUID invoiceId, InvoicePaymentStatus status);
}
