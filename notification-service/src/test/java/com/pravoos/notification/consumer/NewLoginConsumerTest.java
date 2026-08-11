package com.pravoos.notification.consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.pravoos.notification.event.NewLoginKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewLoginConsumerTest {

  private static final String EVENT_TYPE = "user.new_login";

  @Mock private NotificationDispatcher notificationDispatcher;

  private NewLoginConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new NewLoginConsumer(notificationDispatcher);
  }

  @Test
  void onNewLogin_notifiesOnce() {
    NewLoginKafkaPayload payload = payload();

    consumer.onNewLogin(payload);

    verify(notificationDispatcher).dispatchNewLogin(eq(EVENT_TYPE), anyString(), eq(payload));
  }

  private NewLoginKafkaPayload payload() {
    return new NewLoginKafkaPayload(
        UUID.randomUUID(), "203.0.113.9", "JUnit-UA", "2026-07-03 10:15", true, true);
  }
}
