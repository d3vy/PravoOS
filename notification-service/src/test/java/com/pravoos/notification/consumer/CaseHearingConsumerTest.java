package com.pravoos.notification.consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
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

  private CaseHearingConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new CaseHearingConsumer(notificationDispatcher);
  }

  @Test
  void onHearingUpdated_dispatchesOnce() {
    CaseHearingUpdatedKafkaPayload payload = payload();

    consumer.onHearingUpdated(payload);

    verify(notificationDispatcher).dispatchHearingUpdate(eq(EVENT_TYPE), anyString(), eq(payload));
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
