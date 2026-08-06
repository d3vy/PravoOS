package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.BillingProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingProfileRepository extends JpaRepository<BillingProfile, UUID> {}
