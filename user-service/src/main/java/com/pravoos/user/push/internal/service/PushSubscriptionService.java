package com.pravoos.user.push.internal.service;

import com.pravoos.user.push.internal.dto.PushSubscriptionView;
import com.pravoos.user.push.internal.dto.RegisterPushSubscriptionRequest;
import com.pravoos.user.push.internal.model.entity.PushSubscription;
import com.pravoos.user.push.internal.repository.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PushSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionService.class);
    private static final int MAX_SUBSCRIPTIONS_PER_USER = 10;

    private final PushSubscriptionRepository pushSubscriptionRepository;

    public PushSubscriptionService(PushSubscriptionRepository pushSubscriptionRepository) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    @Transactional
    public void register(UUID userId, RegisterPushSubscriptionRequest request) {
        PushSubscription subscription = pushSubscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(PushSubscription::new);
        subscription.setUserId(userId);
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dhKey(request.p256dh());
        subscription.setAuthKey(request.auth());
        subscription.setUserAgent(truncateUserAgent(request.userAgent()));
        subscription.setLastUsedAt(LocalDateTime.now());
        pushSubscriptionRepository.save(subscription);
        evictOldestBeyondLimit(userId);
        log.info("Push subscription registered for user {}", userId);
    }

    @Transactional
    public void unregister(UUID userId, String endpoint) {
        int removed = pushSubscriptionRepository.deleteByEndpointAndUserId(endpoint, userId);
        if (removed > 0) {
            log.info("Push subscription removed for user {}", userId);
        }
    }

    @Transactional
    public void prune(String endpoint) {
        int removed = pushSubscriptionRepository.deleteByEndpoint(endpoint);
        if (removed > 0) {
            log.info("Expired push subscription pruned");
        }
    }

    @Transactional(readOnly = true)
    public List<PushSubscriptionView> subscriptionsOf(UUID userId) {
        return pushSubscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(subscription -> new PushSubscriptionView(
                        subscription.getEndpoint(),
                        subscription.getP256dhKey(),
                        subscription.getAuthKey()))
                .toList();
    }

    private void evictOldestBeyondLimit(UUID userId) {
        long total = pushSubscriptionRepository.countByUserId(userId);
        if (total <= MAX_SUBSCRIPTIONS_PER_USER) {
            return;
        }
        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<PushSubscription> stale = subscriptions.subList(MAX_SUBSCRIPTIONS_PER_USER, subscriptions.size());
        pushSubscriptionRepository.deleteAll(stale);
        log.info("Evicted {} oldest push subscriptions for user {}", stale.size(), userId);
    }

    private String truncateUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() <= 255 ? userAgent : userAgent.substring(0, 255);
    }
}
