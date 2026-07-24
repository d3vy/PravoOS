package com.pravoos.user.billing.api;

import java.util.Optional;
import java.util.UUID;

public interface PlanClaimProvider {

  Optional<PlanClaim> effectivePlanFor(UUID userId);
}
