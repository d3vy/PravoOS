package com.pravoos.user.billing.internal.service;

import com.pravoos.user.billing.internal.model.entity.Subscription;
import com.pravoos.user.billing.internal.model.enums.SubscriptionStatus;
import com.pravoos.user.billing.internal.repository.SubscriptionRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class SubscriptionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycleService.class);
    private static final Set<SubscriptionStatus> RENEWABLE_STATUSES =
            EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING);

    private final SubscriptionRepository subscriptionRepository;
    private final int graceDays;

    public SubscriptionLifecycleService(SubscriptionRepository subscriptionRepository,
                                        @Value("${app.billing.grace-days:5}") int graceDays) {
        this.subscriptionRepository = subscriptionRepository;
        this.graceDays = graceDays;
    }

    @Scheduled(cron = "0 40 3 * * *")
    @SchedulerLock(name = "SubscriptionLifecycleService_sweepExpired", lockAtMostFor = "PT10M")
    @Transactional
    public void sweepExpired() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        expireEndedPeriods(now);
        cancelAfterGrace(now);
    }

    private void expireEndedPeriods(LocalDateTime now) {
        List<Subscription> ended =
                subscriptionRepository.findByStatusInAndCurrentPeriodEndBefore(RENEWABLE_STATUSES, now);
        for (Subscription subscription : ended) {
            if (subscription.isCancelAtPeriodEnd()) {
                subscription.setStatus(SubscriptionStatus.CANCELED);
                log.info("Subscription of user {} canceled at period end", subscription.getUserId());
            } else {
                subscription.setStatus(SubscriptionStatus.PAST_DUE);
                log.info("Subscription of user {} moved to PAST_DUE, grace until {}",
                        subscription.getUserId(), subscription.getCurrentPeriodEnd().plusDays(graceDays));
            }
        }
        subscriptionRepository.saveAll(ended);
    }

    private void cancelAfterGrace(LocalDateTime now) {
        List<Subscription> graceExpired = subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(
                SubscriptionStatus.PAST_DUE, now.minusDays(graceDays));
        for (Subscription subscription : graceExpired) {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            log.info("Subscription of user {} canceled after grace period — downgraded to default plan",
                    subscription.getUserId());
        }
        subscriptionRepository.saveAll(graceExpired);
    }
}
