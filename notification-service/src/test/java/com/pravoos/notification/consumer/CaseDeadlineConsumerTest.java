package com.pravoos.notification.consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseDeadlineConsumerTest {

  private static final String EVENT_TYPE = "case.deadline.approaching";

  @Mock private NotificationDispatcher notificationDispatcher;
  @Mock private ProcessedEventGuard processedEventGuard;

  private CaseDeadlineConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new CaseDeadlineConsumer(notificationDispatcher, processedEventGuard);
  }

  @Test
  void onDeadlineApproaching_dispatchesOnce() {
    CaseDeadlineKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(true);

    consumer.onDeadlineApproaching(payload);

    verify(notificationDispatcher).dispatchDeadline(payload);
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  @Test
  void onDeadlineApproaching_skipsDuplicate() {
    CaseDeadlineKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(false);

    consumer.onDeadlineApproaching(payload);

    verify(notificationDispatcher, never()).dispatchDeadline(payload);
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  private CaseDeadlineKafkaPayload payload() {
    return new CaseDeadlineKafkaPayload(
        UUID.randomUUID(), UUID.randomUUID(), "Case A", "FILING_DEADLINE", "2026-08-10", 3);
  }
}
