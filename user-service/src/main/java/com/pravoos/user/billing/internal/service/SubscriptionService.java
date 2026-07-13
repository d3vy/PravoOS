package com.pravoos.user.billing.internal.service;

import com.pravoos.user.billing.api.PlanClaim;
import com.pravoos.user.billing.internal.dto.BillingStatusResponse;
import com.pravoos.user.billing.internal.model.entity.Plan;
import com.pravoos.user.billing.internal.model.entity.Subscription;
import com.pravoos.user.billing.internal.model.enums.SubscriptionStatus;
import com.pravoos.user.billing.internal.repository.PlanRepository;
import com.pravoos.user.billing.internal.repository.SubscriptionRepository;
import com.pravoos.user.shared.exception.PravoosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final Set<SubscriptionStatus> ENTITLED_STATUSES =
            EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING);

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final String trialPlanCode;
    private final int trialDays;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               PlanRepository planRepository,
                               @Value("${app.billing.trial-plan-code:SOLO}") String trialPlanCode,
                               @Value("${app.billing.trial-days:14}") int trialDays) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.trialPlanCode = trialPlanCode;
        this.trialDays = trialDays;
    }

    @Transactional
    public void startTrial(UUID userId) {
        if (subscriptionRepository.existsByUserId(userId)) {
            return;
        }
        Plan trialPlan = planRepository.findByCode(trialPlanCode)
                .orElseThrow(() -> planNotFound(trialPlanCode));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime end = now.plusDays(trialDays);

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanId(trialPlan.getId());
        subscription.setStatus(SubscriptionStatus.TRIALING);
        subscription.setTrialEnd(end);
        subscription.setCurrentPeriodEnd(end);
        subscriptionRepository.save(subscription);

        log.info("Trial subscription started for user {} on plan {}", userId, trialPlanCode);
    }

    @Transactional(readOnly = true)
    public Optional<PlanClaim> effectivePlanFor(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .filter(subscription -> ENTITLED_STATUSES.contains(subscription.getStatus()))
                .flatMap(subscription -> planRepository.findById(subscription.getPlanId()))
                .or(planRepository::findByIsDefaultTrue)
                .map(plan -> new PlanClaim(plan.getCode(), plan.getDailyRequests(), plan.getDailyTokens()));
    }

    @Transactional
    public BillingStatusResponse getStatus(UUID userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSubscription(userId));
        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> planNotFound(subscription.getPlanId().toString()));
        return toResponse(subscription, plan);
    }

    private Subscription createDefaultSubscription(UUID userId) {
        Plan defaultPlan = planRepository.findByIsDefaultTrue()
                .orElseThrow(() -> planNotFound("default"));
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanId(defaultPlan.getId());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        return subscriptionRepository.save(subscription);
    }

    private BillingStatusResponse toResponse(Subscription subscription, Plan plan) {
        return new BillingStatusResponse(
                plan.getCode(),
                plan.getName(),
                subscription.getStatus(),
                subscription.getTrialEnd(),
                subscription.getCurrentPeriodEnd(),
                plan.getDailyRequests(),
                plan.getDailyTokens(),
                plan.getSeats());
    }

    private PravoosException planNotFound(String plan) {
        return new PravoosException("Тариф не найден: " + plan, HttpStatus.INTERNAL_SERVER_ERROR, "PLAN_NOT_FOUND");
    }
}
