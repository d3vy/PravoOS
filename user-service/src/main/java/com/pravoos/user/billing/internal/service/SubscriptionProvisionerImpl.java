package com.pravoos.user.billing.internal.service;

import com.pravoos.user.billing.api.PlanClaim;
import com.pravoos.user.billing.api.PlanClaimProvider;
import com.pravoos.user.billing.api.SubscriptionProvisioner;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionProvisionerImpl implements SubscriptionProvisioner, PlanClaimProvider {

  private final SubscriptionService subscriptionService;

  public SubscriptionProvisionerImpl(SubscriptionService subscriptionService) {
    this.subscriptionService = subscriptionService;
  }

  @Override
  public void startTrial(UUID userId) {
    subscriptionService.startTrial(userId);
  }

  @Override
  public Optional<PlanClaim> effectivePlanFor(UUID userId) {
    return subscriptionService.effectivePlanFor(userId);
  }
}
