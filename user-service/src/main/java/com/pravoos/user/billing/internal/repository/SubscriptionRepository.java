package com.pravoos.user.billing.internal.repository;

import com.pravoos.user.billing.internal.model.entity.Subscription;
import com.pravoos.user.billing.internal.model.enums.SubscriptionStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

  Optional<Subscription> findByUserId(UUID userId);

  boolean existsByUserId(UUID userId);

  List<Subscription> findByStatusInAndCurrentPeriodEndBefore(
      Collection<SubscriptionStatus> statuses, LocalDateTime threshold);

  List<Subscription> findByStatusAndCurrentPeriodEndBefore(
      SubscriptionStatus status, LocalDateTime threshold);
}
