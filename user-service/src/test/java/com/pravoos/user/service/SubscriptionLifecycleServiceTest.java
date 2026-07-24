package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.pravoos.user.billing.internal.model.entity.Subscription;
import com.pravoos.user.billing.internal.model.enums.SubscriptionStatus;
import com.pravoos.user.billing.internal.repository.SubscriptionRepository;
import com.pravoos.user.billing.internal.service.SubscriptionLifecycleService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleServiceTest {

  private static final int GRACE_DAYS = 5;

  @Mock private SubscriptionRepository subscriptionRepository;

  private SubscriptionLifecycleService lifecycleService;

  @BeforeEach
  void setup() {
    lifecycleService = new SubscriptionLifecycleService(subscriptionRepository, GRACE_DAYS);
  }

  @Test
  void expiredSubscriptionMovesToPastDue() {
    Subscription expired = subscription(SubscriptionStatus.ACTIVE, false);
    when(subscriptionRepository.findByStatusInAndCurrentPeriodEndBefore(anyCollection(), any()))
        .thenReturn(List.of(expired));
    when(subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(any(), any()))
        .thenReturn(List.of());

    lifecycleService.sweepExpired();

    assertThat(expired.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
  }

  @Test
  void expiredSubscriptionWithPendingCancellationIsCanceled() {
    Subscription expired = subscription(SubscriptionStatus.ACTIVE, true);
    when(subscriptionRepository.findByStatusInAndCurrentPeriodEndBefore(anyCollection(), any()))
        .thenReturn(List.of(expired));
    when(subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(any(), any()))
        .thenReturn(List.of());

    lifecycleService.sweepExpired();

    assertThat(expired.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
  }

  @Test
  void pastDueSubscriptionIsCanceledAfterGracePeriod() {
    Subscription graceExpired = subscription(SubscriptionStatus.PAST_DUE, false);
    when(subscriptionRepository.findByStatusInAndCurrentPeriodEndBefore(anyCollection(), any()))
        .thenReturn(List.of());
    when(subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(
            eq(SubscriptionStatus.PAST_DUE), any()))
        .thenReturn(List.of(graceExpired));

    lifecycleService.sweepExpired();

    assertThat(graceExpired.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
  }

  @Test
  void sweepOnlyLooksAtRenewableStatuses() {
    when(subscriptionRepository.findByStatusInAndCurrentPeriodEndBefore(anyCollection(), any()))
        .thenAnswer(
            call -> {
              Collection<SubscriptionStatus> statuses = call.getArgument(0);
              assertThat(statuses)
                  .containsExactlyInAnyOrder(
                      SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING);
              return List.of();
            });
    when(subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(any(), any()))
        .thenReturn(List.of());

    lifecycleService.sweepExpired();
  }

  private Subscription subscription(SubscriptionStatus status, boolean cancelAtPeriodEnd) {
    Subscription subscription = new Subscription();
    subscription.setUserId(UUID.randomUUID());
    subscription.setPlanId(UUID.randomUUID());
    subscription.setStatus(status);
    subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
    subscription.setCurrentPeriodEnd(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
    return subscription;
  }
}
