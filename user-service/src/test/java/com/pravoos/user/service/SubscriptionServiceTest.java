package com.pravoos.user.service;

import com.pravoos.user.billing.api.PlanClaim;
import com.pravoos.user.billing.internal.dto.BillingStatusResponse;
import com.pravoos.user.billing.internal.model.entity.Plan;
import com.pravoos.user.billing.internal.model.entity.Subscription;
import com.pravoos.user.billing.internal.model.enums.SubscriptionStatus;
import com.pravoos.user.billing.internal.repository.PlanRepository;
import com.pravoos.user.billing.internal.repository.SubscriptionRepository;
import com.pravoos.user.billing.internal.service.SubscriptionService;
import com.pravoos.user.shared.exception.PravoosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final String TRIAL_PLAN_CODE = "SOLO";
    private static final int TRIAL_DAYS = 14;
    private static final int GRACE_DAYS = 5;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    private SubscriptionService subscriptionService;
    private UUID userId;
    private Plan trialPlan;
    private Plan defaultPlan;

    @BeforeEach
    void setup() {
        subscriptionService = new SubscriptionService(
                subscriptionRepository, planRepository, TRIAL_PLAN_CODE, TRIAL_DAYS, GRACE_DAYS);
        userId = UUID.randomUUID();
        trialPlan = buildPlan(TRIAL_PLAN_CODE, "Solo", 200, 200000L, 1);
        defaultPlan = buildPlan("FREE", "Free", 20, 20000L, 1);
    }

    @Test
    void startTrialCreatesTrialingSubscriptionOnTrialPlan() {
        when(subscriptionRepository.existsByUserId(userId)).thenReturn(false);
        when(planRepository.findByCode(TRIAL_PLAN_CODE)).thenReturn(Optional.of(trialPlan));

        subscriptionService.startTrial(userId);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription created = captor.getValue();

        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getPlanId()).isEqualTo(trialPlan.getId());
        assertThat(created.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(created.getTrialEnd()).isNotNull();
        assertThat(created.getCurrentPeriodEnd()).isEqualTo(created.getTrialEnd());
    }

    @Test
    void startTrialIsIdempotentWhenSubscriptionAlreadyExists() {
        when(subscriptionRepository.existsByUserId(userId)).thenReturn(true);

        subscriptionService.startTrial(userId);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void startTrialFailsWhenTrialPlanMissing() {
        when(subscriptionRepository.existsByUserId(userId)).thenReturn(false);
        when(planRepository.findByCode(TRIAL_PLAN_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.startTrial(userId))
                .isInstanceOf(PravoosException.class)
                .hasMessageContaining(TRIAL_PLAN_CODE);
    }

    @Test
    void getStatusCreatesDefaultSubscriptionWhenAbsent() {
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(planRepository.findByIsDefaultTrue()).thenReturn(Optional.of(defaultPlan));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(call -> call.getArgument(0));
        when(planRepository.findById(defaultPlan.getId())).thenReturn(Optional.of(defaultPlan));

        BillingStatusResponse response = subscriptionService.getStatus(userId);

        assertThat(response.planCode()).isEqualTo("FREE");
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.dailyRequests()).isEqualTo(20);
        assertThat(response.trialEnd()).isNull();
    }

    @Test
    void getStatusReturnsExistingSubscriptionWithPlanLimits() {
        Subscription existing = new Subscription();
        existing.setUserId(userId);
        existing.setPlanId(trialPlan.getId());
        existing.setStatus(SubscriptionStatus.TRIALING);
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(planRepository.findById(trialPlan.getId())).thenReturn(Optional.of(trialPlan));

        BillingStatusResponse response = subscriptionService.getStatus(userId);

        assertThat(response.planCode()).isEqualTo(TRIAL_PLAN_CODE);
        assertThat(response.status()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(response.dailyTokens()).isEqualTo(200000L);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void effectivePlanReturnsSubscriptionPlanWhenEntitled() {
        when(subscriptionRepository.findByUserId(userId))
                .thenReturn(Optional.of(subscriptionOn(trialPlan, SubscriptionStatus.TRIALING)));
        when(planRepository.findById(trialPlan.getId())).thenReturn(Optional.of(trialPlan));

        PlanClaim claim = subscriptionService.effectivePlanFor(userId).orElseThrow();

        assertThat(claim.code()).isEqualTo(TRIAL_PLAN_CODE);
        assertThat(claim.dailyRequests()).isEqualTo(200);
        assertThat(claim.dailyTokens()).isEqualTo(200000L);
    }

    @Test
    void pastDueKeepsPlanWhileInsideGracePeriod() {
        Subscription pastDue = subscriptionOn(trialPlan, SubscriptionStatus.PAST_DUE);
        pastDue.setCurrentPeriodEnd(LocalDateTime.now(ZoneOffset.UTC).minusDays(GRACE_DAYS - 1));
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(pastDue));
        when(planRepository.findById(trialPlan.getId())).thenReturn(Optional.of(trialPlan));

        assertThat(subscriptionService.effectivePlanFor(userId).orElseThrow().code()).isEqualTo(TRIAL_PLAN_CODE);
    }

    @Test
    void pastDueFallsBackToDefaultPlanAfterGracePeriod() {
        Subscription pastDue = subscriptionOn(trialPlan, SubscriptionStatus.PAST_DUE);
        pastDue.setCurrentPeriodEnd(LocalDateTime.now(ZoneOffset.UTC).minusDays(GRACE_DAYS + 1));
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(pastDue));
        when(planRepository.findByIsDefaultTrue()).thenReturn(Optional.of(defaultPlan));

        PlanClaim claim = subscriptionService.effectivePlanFor(userId).orElseThrow();

        assertThat(claim.code()).isEqualTo("FREE");
        assertThat(claim.dailyRequests()).isEqualTo(20);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void canceledSubscriptionFallsBackToDefaultPlan() {
        when(subscriptionRepository.findByUserId(userId))
                .thenReturn(Optional.of(subscriptionOn(trialPlan, SubscriptionStatus.CANCELED)));
        when(planRepository.findByIsDefaultTrue()).thenReturn(Optional.of(defaultPlan));

        assertThat(subscriptionService.effectivePlanFor(userId).orElseThrow().code()).isEqualTo("FREE");
    }

    @Test
    void effectivePlanFallsBackToDefaultPlanWhenNoSubscription() {
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(planRepository.findByIsDefaultTrue()).thenReturn(Optional.of(defaultPlan));

        assertThat(subscriptionService.effectivePlanFor(userId).orElseThrow().code()).isEqualTo("FREE");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void cancelKeepsAccessUntilPeriodEnd() {
        Subscription existing = subscriptionOn(trialPlan, SubscriptionStatus.ACTIVE);
        existing.setCurrentPeriodEnd(LocalDateTime.now(ZoneOffset.UTC).plusDays(10));
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(planRepository.findById(trialPlan.getId())).thenReturn(Optional.of(trialPlan));

        BillingStatusResponse response = subscriptionService.cancel(userId);

        assertThat(existing.isCancelAtPeriodEnd()).isTrue();
        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.cancelAtPeriodEnd()).isTrue();
        verify(subscriptionRepository).save(existing);
    }

    @Test
    void cancelFailsWithoutActiveSubscription() {
        when(subscriptionRepository.findByUserId(userId))
                .thenReturn(Optional.of(subscriptionOn(trialPlan, SubscriptionStatus.CANCELED)));

        assertThatThrownBy(() -> subscriptionService.cancel(userId))
                .isInstanceOf(PravoosException.class);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void activateOnPlanExtendsPeriodAndClearsPendingCancellation() {
        Subscription existing = subscriptionOn(trialPlan, SubscriptionStatus.ACTIVE);
        LocalDateTime periodEnd = LocalDateTime.now(ZoneOffset.UTC).plusDays(5);
        existing.setCurrentPeriodEnd(periodEnd);
        existing.setCancelAtPeriodEnd(true);
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        subscriptionService.activateOnPlan(userId, trialPlan.getId(), 30);

        assertThat(existing.isCancelAtPeriodEnd()).isFalse();
        assertThat(existing.getCurrentPeriodEnd()).isEqualTo(periodEnd.plusDays(30));
        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    private Subscription subscriptionOn(Plan plan, SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanId(plan.getId());
        subscription.setStatus(status);
        return subscription;
    }

    private Plan buildPlan(String code, String name, int dailyRequests, long dailyTokens, int seats) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        plan.setCode(code);
        plan.setName(name);
        plan.setDailyRequests(dailyRequests);
        plan.setDailyTokens(dailyTokens);
        plan.setSeats(seats);
        return plan;
    }
}
