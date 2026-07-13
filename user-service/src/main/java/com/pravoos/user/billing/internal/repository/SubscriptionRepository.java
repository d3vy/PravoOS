package com.pravoos.user.billing.internal.repository;

import com.pravoos.user.billing.internal.model.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
