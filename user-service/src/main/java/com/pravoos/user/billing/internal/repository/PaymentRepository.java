package com.pravoos.user.billing.internal.repository;

import com.pravoos.user.billing.internal.model.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
