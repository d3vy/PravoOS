package com.pravoos.notification.push;

import com.pravoos.notification.client.PushSubscriptionResponse;

public interface WebPushSender {

    PushDeliveryStatus send(PushSubscriptionResponse subscription, PushMessage message);
}
