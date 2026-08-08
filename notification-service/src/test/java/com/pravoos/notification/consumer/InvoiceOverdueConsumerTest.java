package com.pravoos.notification.consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.notification.event.InvoiceOverdueKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceOverdueConsumerTest {

  private static final String EVENT_TYPE = "invoice.overdue";

  @Mock private NotificationDispatcher notificationDispatcher;
  @Mock private ProcessedEventGuard processedEventGuard;

  private InvoiceOverdueConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new InvoiceOverdueConsumer(notificationDispatcher, processedEventGuard);
  }

  @Test
  void onInvoiceOverdue_dispatchesOnce() {
    InvoiceOverdueKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(true);

    consumer.onInvoiceOverdue(payload);

    verify(notificationDispatcher).dispatchInvoiceOverdue(payload);
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  @Test
  void onInvoiceOverdue_skipsDuplicate() {
    InvoiceOverdueKafkaPayload payload = payload();
    when(processedEventGuard.claim(eq(EVENT_TYPE), anyString())).thenReturn(false);

    consumer.onInvoiceOverdue(payload);

    verify(notificationDispatcher, never()).dispatchInvoiceOverdue(payload);
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  private InvoiceOverdueKafkaPayload payload() {
    return new InvoiceOverdueKafkaPayload(
        UUID.randomUUID(), UUID.randomUUID(), "INV-001", "Client A", "10 000 ₽", 5);
  }
}
