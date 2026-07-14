package com.pravoos.notification.service;

import com.pravoos.notification.client.PushSubscriptionResponse;
import com.pravoos.notification.client.UserServiceClient;
import com.pravoos.notification.push.PushDeliveryStatus;
import com.pravoos.notification.push.PushMessage;
import com.pravoos.notification.push.WebPushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final UserServiceClient userServiceClient;
    private final WebPushSender webPushSender;

    public PushNotificationService(UserServiceClient userServiceClient, WebPushSender webPushSender) {
        this.userServiceClient = userServiceClient;
        this.webPushSender = webPushSender;
    }

    public int notifyUser(UUID userId, PushMessage message) {
        if (userId == null) {
            return 0;
        }
        List<PushSubscriptionResponse> subscriptions = loadSubscriptions(userId);
        if (subscriptions.isEmpty()) {
            log.debug("User {} has no push subscriptions, skipping [{}]", userId, message.tag());
            return 0;
        }
        int delivered = 0;
        for (PushSubscriptionResponse subscription : subscriptions) {
            PushDeliveryStatus status = webPushSender.send(subscription, message);
            if (status == PushDeliveryStatus.DELIVERED) {
                delivered++;
            } else if (status == PushDeliveryStatus.EXPIRED) {
                pruneSubscription(subscription.endpoint());
            }
        }
        log.info("Web push [{}] delivered to {}/{} devices of user {}",
                message.tag(), delivered, subscriptions.size(), userId);
        return delivered;
    }

    private List<PushSubscriptionResponse> loadSubscriptions(UUID userId) {
        try {
            return userServiceClient.listPushSubscriptions(userId);
        } catch (Exception e) {
            log.error("Failed to load push subscriptions for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private void pruneSubscription(String endpoint) {
        try {
            userServiceClient.prunePushSubscription(endpoint);
        } catch (Exception e) {
            log.warn("Failed to prune expired push subscription: {}", e.getMessage());
        }
    }
}
