package com.pravoos.notification.consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.pravoos.notification.event.InvoiceOverdueKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
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

  private InvoiceOverdueConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new InvoiceOverdueConsumer(notificationDispatcher);
  }

  @Test
  void onInvoiceOverdue_dispatchesOnce() {
    InvoiceOverdueKafkaPayload payload = payload();

    consumer.onInvoiceOverdue(payload);

    verify(notificationDispatcher).dispatchInvoiceOverdue(eq(EVENT_TYPE), anyString(), eq(payload));
  }

  private InvoiceOverdueKafkaPayload payload() {
    return new InvoiceOverdueKafkaPayload(
        UUID.randomUUID(), UUID.randomUUID(), "INV-001", "Client A", "10 000 ₽", 5);
  }
}
