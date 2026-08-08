package com.pravoos.notification.consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.notification.event.CaseMessageCreatedKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseMessageConsumerTest {

  private static final String EVENT_TYPE = "case.message.created";

  @Mock private NotificationDispatcher notificationDispatcher;
  @Mock private ProcessedEventGuard processedEventGuard;

  private CaseMessageConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new CaseMessageConsumer(notificationDispatcher, processedEventGuard);
  }

  @Test
  void onCaseMessageCreated_dispatchesOnce() {
    CaseMessageCreatedKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(true);

    consumer.onCaseMessageCreated(payload);

    verify(notificationDispatcher).dispatchCaseMessage(payload);
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  @Test
  void onCaseMessageCreated_skipsDuplicate() {
    CaseMessageCreatedKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(false);

    consumer.onCaseMessageCreated(payload);

    verify(notificationDispatcher, never()).dispatchCaseMessage(payload);
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  private CaseMessageCreatedKafkaPayload payload() {
    return new CaseMessageCreatedKafkaPayload(
        UUID.randomUUID(),
        "Case A",
        UUID.randomUUID(),
        "LAWYER",
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        "Hello");
  }
}
