package com.pravoos.user.billing.internal.repository;

import com.pravoos.user.billing.internal.model.entity.Payment;
import com.pravoos.user.billing.internal.model.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Payment p WHERE p.providerPaymentId = :providerPaymentId")
  Optional<Payment> findByProviderPaymentIdForUpdate(
      @Param("providerPaymentId") String providerPaymentId);

  List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);

  List<Payment> findByUserIdAndPlanIdAndStatusOrderByCreatedAtDesc(
      UUID userId, UUID planId, PaymentStatus status);
}
