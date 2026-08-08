package com.pravoos.notification.consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.notification.event.InvoicePaidKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoicePaidConsumerTest {

  private static final String EVENT_TYPE = "invoice.paid";

  @Mock private NotificationDispatcher notificationDispatcher;
  @Mock private ProcessedEventGuard processedEventGuard;

  private InvoicePaidConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new InvoicePaidConsumer(notificationDispatcher, processedEventGuard);
  }

  @Test
  void onInvoicePaid_dispatchesOnce() {
    InvoicePaidKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(true);

    consumer.onInvoicePaid(payload);

    verify(notificationDispatcher).dispatchInvoicePaid(payload);
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  @Test
  void onInvoicePaid_skipsDuplicate() {
    InvoicePaidKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(false);

    consumer.onInvoicePaid(payload);

    verify(notificationDispatcher, never()).dispatchInvoicePaid(payload);
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  @Test
  void onInvoicePaid_releasesClaimWhenDispatchFailsSoRetryCanReprocess() {
    InvoicePaidKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(true);
    org.mockito.Mockito.doThrow(new IllegalStateException("telegram down"))
        .when(notificationDispatcher)
        .dispatchInvoicePaid(payload);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.onInvoicePaid(payload))
        .isInstanceOf(IllegalStateException.class);

    verify(processedEventGuard).release(EVENT_TYPE, payload.invoiceId().toString());
  }

  private InvoicePaidKafkaPayload payload() {
    return new InvoicePaidKafkaPayload(
        UUID.randomUUID(), UUID.randomUUID(), "INV-001", "Client A", "10 000 ₽");
  }
}
