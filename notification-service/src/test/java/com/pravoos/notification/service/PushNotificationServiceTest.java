package com.pravoos.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pravoos.notification.client.PushSubscriptionResponse;
import com.pravoos.notification.client.UserServiceClient;
import com.pravoos.notification.push.PushDeliveryStatus;
import com.pravoos.notification.push.PushMessage;
import com.pravoos.notification.push.WebPushSender;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

  private static final PushMessage MESSAGE =
      PushMessage.of("Дедлайн", "Осталось 3 дня", "/cases/1", "deadline-1");

  @Mock private UserServiceClient userServiceClient;
  @Mock private WebPushSender webPushSender;

  private PushNotificationService service;

  @BeforeEach
  void setUp() {
    service = new PushNotificationService(userServiceClient, webPushSender);
  }

  @Test
  void notifyUser_sendsToEveryDevice() {
    UUID userId = UUID.randomUUID();
    when(userServiceClient.listPushSubscriptions(userId))
        .thenReturn(
            List.of(subscription("https://fcm.example/a"), subscription("https://fcm.example/b")));
    when(webPushSender.send(any(), eq(MESSAGE))).thenReturn(PushDeliveryStatus.DELIVERED);

    int delivered = service.notifyUser(userId, MESSAGE);

    assertThat(delivered).isEqualTo(2);
    verify(userServiceClient, never()).prunePushSubscription(anyString());
  }

  @Test
  void notifyUser_prunesExpiredEndpoints() {
    UUID userId = UUID.randomUUID();
    PushSubscriptionResponse alive = subscription("https://fcm.example/alive");
    PushSubscriptionResponse expired = subscription("https://fcm.example/expired");
    when(userServiceClient.listPushSubscriptions(userId)).thenReturn(List.of(alive, expired));
    when(webPushSender.send(alive, MESSAGE)).thenReturn(PushDeliveryStatus.DELIVERED);
    when(webPushSender.send(expired, MESSAGE)).thenReturn(PushDeliveryStatus.EXPIRED);

    int delivered = service.notifyUser(userId, MESSAGE);

    assertThat(delivered).isEqualTo(1);
    verify(userServiceClient).prunePushSubscription("https://fcm.example/expired");
  }

  @Test
  void notifyUser_keepsSubscription_whenDeliveryFailedTemporarily() {
    UUID userId = UUID.randomUUID();
    when(userServiceClient.listPushSubscriptions(userId))
        .thenReturn(List.of(subscription("https://fcm.example/a")));
    when(webPushSender.send(any(), eq(MESSAGE))).thenReturn(PushDeliveryStatus.FAILED);

    int delivered = service.notifyUser(userId, MESSAGE);

    assertThat(delivered).isZero();
    verify(userServiceClient, never()).prunePushSubscription(anyString());
  }

  @Test
  void notifyUser_skips_whenNoSubscriptions() {
    UUID userId = UUID.randomUUID();
    when(userServiceClient.listPushSubscriptions(userId)).thenReturn(List.of());

    int delivered = service.notifyUser(userId, MESSAGE);

    assertThat(delivered).isZero();
    verifyNoInteractions(webPushSender);
  }

  @Test
  void notifyUser_survivesUserServiceOutage() {
    UUID userId = UUID.randomUUID();
    when(userServiceClient.listPushSubscriptions(userId))
        .thenThrow(new IllegalStateException("user-service down"));

    int delivered = service.notifyUser(userId, MESSAGE);

    assertThat(delivered).isZero();
    verifyNoInteractions(webPushSender);
  }

  @Test
  void notifyUser_ignoresNullRecipient() {
    int delivered = service.notifyUser(null, MESSAGE);

    assertThat(delivered).isZero();
    verifyNoInteractions(userServiceClient, webPushSender);
  }

  private PushSubscriptionResponse subscription(String endpoint) {
    return new PushSubscriptionResponse(endpoint, "p256dh-key", "auth-key");
  }
}
