package com.pravoos.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.notification.client.PushSubscriptionResponse;
import org.junit.jupiter.api.Test;

class NoopWebPushSenderTest {

  private final NoopWebPushSender sender = new NoopWebPushSender();

  @Test
  void send_alwaysReportsFailed_withoutThrowing() {
    PushSubscriptionResponse subscription =
        new PushSubscriptionResponse("https://push.example.com/1", "p256dh", "auth");
    PushMessage message = PushMessage.of("Title", "Body", "https://app.example.com", "tag-1");

    PushDeliveryStatus status = sender.send(subscription, message);

    assertThat(status).isEqualTo(PushDeliveryStatus.FAILED);
  }
}
