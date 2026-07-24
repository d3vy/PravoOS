package com.pravoos.user.push.internal.repository;

import com.pravoos.user.push.internal.model.entity.PushSubscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

  Optional<PushSubscription> findByEndpoint(String endpoint);

  List<PushSubscription> findByUserIdOrderByCreatedAtDesc(UUID userId);

  long countByUserId(UUID userId);

  @Modifying
  @Query("DELETE FROM PushSubscription s WHERE s.endpoint = :endpoint")
  int deleteByEndpoint(@Param("endpoint") String endpoint);

  @Modifying
  @Query("DELETE FROM PushSubscription s WHERE s.endpoint = :endpoint AND s.userId = :userId")
  int deleteByEndpointAndUserId(@Param("endpoint") String endpoint, @Param("userId") UUID userId);
}
