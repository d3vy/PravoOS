package com.pravoos.notification.consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseHearingConsumerTest {

  private static final String EVENT_TYPE = "case.hearing.updated";

  @Mock private NotificationDispatcher notificationDispatcher;
  @Mock private ProcessedEventGuard processedEventGuard;

  private CaseHearingConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new CaseHearingConsumer(notificationDispatcher, processedEventGuard);
  }

  @Test
  void onHearingUpdated_dispatchesOnce() {
    CaseHearingUpdatedKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(true);

    consumer.onHearingUpdated(payload);

    verify(notificationDispatcher).dispatchHearingUpdate(payload);
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  @Test
  void onHearingUpdated_skipsDuplicate() {
    CaseHearingUpdatedKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(false);

    consumer.onHearingUpdated(payload);

    verify(notificationDispatcher, never()).dispatchHearingUpdate(payload);
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  private CaseHearingUpdatedKafkaPayload payload() {
    return new CaseHearingUpdatedKafkaPayload(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Case A",
        "A40-1234/2026",
        "2026-08-01",
        "2026-08-15");
  }
}
