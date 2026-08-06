package com.pravoos.user.billing.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.billing.api.PlanClaim;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionProvisionerImplTest {

  @Mock private SubscriptionService subscriptionService;

  private SubscriptionProvisionerImpl provisioner;

  @BeforeEach
  void setUp() {
    provisioner = new SubscriptionProvisionerImpl(subscriptionService);
  }

  @Test
  void startTrial_delegatesToSubscriptionService() {
    UUID userId = UUID.randomUUID();

    provisioner.startTrial(userId);

    verify(subscriptionService).startTrial(userId);
  }

  @Test
  void effectivePlanFor_delegatesAndReturnsResult() {
    UUID userId = UUID.randomUUID();
    PlanClaim claim = new PlanClaim("PRO", 100, 50_000L);
    when(subscriptionService.effectivePlanFor(userId)).thenReturn(Optional.of(claim));

    Optional<PlanClaim> result = provisioner.effectivePlanFor(userId);

    assertThat(result).contains(claim);
  }

  @Test
  void effectivePlanFor_returnsEmptyWhenNoSubscription() {
    UUID userId = UUID.randomUUID();
    when(subscriptionService.effectivePlanFor(userId)).thenReturn(Optional.empty());

    Optional<PlanClaim> result = provisioner.effectivePlanFor(userId);

    assertThat(result).isEmpty();
  }
}
