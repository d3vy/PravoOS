package com.pravoos.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseDeadlineConsumerTest {

  private static final String EVENT_TYPE = "case.deadline.approaching";

  @Mock private NotificationDispatcher notificationDispatcher;

  private CaseDeadlineConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new CaseDeadlineConsumer(notificationDispatcher);
  }

  @Test
  void onDeadlineApproaching_dispatchesOnce() {
    CaseDeadlineKafkaPayload payload = payload();

    consumer.onDeadlineApproaching(payload);

    verify(notificationDispatcher).dispatchDeadline(eq(EVENT_TYPE), anyString(), eq(payload));
  }

  @Test
  void dedupKeyDistinguishesRescheduledDeadlines() {
    CaseDeadlineKafkaPayload payload = payload();

    consumer.onDeadlineApproaching(payload);

    ArgumentCaptor<String> dedupKey = ArgumentCaptor.forClass(String.class);
    verify(notificationDispatcher)
        .dispatchDeadline(eq(EVENT_TYPE), dedupKey.capture(), eq(payload));
    assertThat(dedupKey.getValue()).contains(payload.deadlineDate());
  }

  private CaseDeadlineKafkaPayload payload() {
    return new CaseDeadlineKafkaPayload(
        UUID.randomUUID(), UUID.randomUUID(), "Case A", "FILING_DEADLINE", "2026-08-10", 3);
  }
}
