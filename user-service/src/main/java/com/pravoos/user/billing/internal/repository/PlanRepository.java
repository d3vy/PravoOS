package com.pravoos.user.billing.internal.repository;

import com.pravoos.user.billing.internal.model.entity.Plan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

  Optional<Plan> findByCode(String code);

  Optional<Plan> findByIsDefaultTrue();
}
