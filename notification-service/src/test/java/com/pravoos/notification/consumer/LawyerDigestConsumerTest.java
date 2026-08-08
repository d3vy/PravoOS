package com.pravoos.notification.consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.notification.event.LawyerDigestKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LawyerDigestConsumerTest {

  private static final String EVENT_TYPE = "lawyer.digest.morning";

  @Mock private NotificationDispatcher notificationDispatcher;
  @Mock private ProcessedEventGuard processedEventGuard;

  private LawyerDigestConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LawyerDigestConsumer(notificationDispatcher, processedEventGuard);
  }

  @Test
  void onMorningDigest_dispatchesOnce() {
    LawyerDigestKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(true);

    consumer.onMorningDigest(payload);

    verify(notificationDispatcher).dispatchMorningDigest(payload);
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  @Test
  void onMorningDigest_skipsDuplicate() {
    LawyerDigestKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(false);

    consumer.onMorningDigest(payload);

    verify(notificationDispatcher, never()).dispatchMorningDigest(payload);
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  private LawyerDigestKafkaPayload payload() {
    return new LawyerDigestKafkaPayload(UUID.randomUUID(), "2026-08-07", 3, 2, 1, "5 000 ₽");
  }
}
