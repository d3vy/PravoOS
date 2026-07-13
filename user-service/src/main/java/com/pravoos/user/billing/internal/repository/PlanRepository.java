package com.pravoos.user.billing.internal.repository;

import com.pravoos.user.billing.internal.model.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    Optional<Plan> findByCode(String code);

    Optional<Plan> findByIsDefaultTrue();
}
