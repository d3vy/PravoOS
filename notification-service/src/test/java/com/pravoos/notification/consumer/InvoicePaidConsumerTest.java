package com.pravoos.notification.consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.pravoos.notification.event.InvoicePaidKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
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

  private InvoicePaidConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new InvoicePaidConsumer(notificationDispatcher);
  }

  @Test
  void onInvoicePaid_dispatchesOnce() {
    InvoicePaidKafkaPayload payload = payload();

    consumer.onInvoicePaid(payload);

    verify(notificationDispatcher).dispatchInvoicePaid(eq(EVENT_TYPE), anyString(), eq(payload));
  }

  private InvoicePaidKafkaPayload payload() {
    return new InvoicePaidKafkaPayload(
        UUID.randomUUID(), UUID.randomUUID(), "INV-001", "Client A", "10 000 ₽");
  }
}
