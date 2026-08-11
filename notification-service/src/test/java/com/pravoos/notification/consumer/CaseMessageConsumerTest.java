package com.pravoos.notification.consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.pravoos.notification.event.CaseMessageCreatedKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
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

  private CaseMessageConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new CaseMessageConsumer(notificationDispatcher);
  }

  @Test
  void onCaseMessageCreated_dispatchesOnce() {
    CaseMessageCreatedKafkaPayload payload = payload();

    consumer.onCaseMessageCreated(payload);

    verify(notificationDispatcher).dispatchCaseMessage(eq(EVENT_TYPE), anyString(), eq(payload));
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
