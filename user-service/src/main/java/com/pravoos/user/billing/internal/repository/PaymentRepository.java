package com.pravoos.user.billing.internal.repository;

import com.pravoos.user.billing.internal.model.entity.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Optional<Payment> findByProviderPaymentId(String providerPaymentId);

  List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
