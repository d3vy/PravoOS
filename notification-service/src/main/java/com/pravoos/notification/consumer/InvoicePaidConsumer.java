package com.pravoos.notification.consumer;

import com.pravoos.notification.event.InvoicePaidKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InvoicePaidConsumer {

  private static final Logger log = LoggerFactory.getLogger(InvoicePaidConsumer.class);
  private static final String EVENT_TYPE = "invoice.paid";

  private final NotificationDispatcher notificationDispatcher;

  public InvoicePaidConsumer(NotificationDispatcher notificationDispatcher) {
    this.notificationDispatcher = notificationDispatcher;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "invoicePaidKafkaListenerContainerFactory")
  public void onInvoicePaid(InvoicePaidKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.invoiceId()));
    try {
      String dedupKey = String.valueOf(payload.invoiceId());
      log.info("Received invoice.paid: invoice={}", payload.invoiceId());
      notificationDispatcher.dispatchInvoicePaid(EVENT_TYPE, dedupKey, payload);
    } finally {
      MDC.remove("requestId");
    }
  }
}
