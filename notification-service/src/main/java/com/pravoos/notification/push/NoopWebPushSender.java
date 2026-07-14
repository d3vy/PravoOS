package com.pravoos.notification.push;

import com.pravoos.notification.client.PushSubscriptionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopWebPushSender implements WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(NoopWebPushSender.class);

    @Override
    public PushDeliveryStatus send(PushSubscriptionResponse subscription, PushMessage message) {
        log.debug("Web push disabled (no VAPID keys), skipping message [{}]", message.tag());
        return PushDeliveryStatus.FAILED;
    }
}
