package com.pravoos.notification.consumer;

import com.pravoos.notification.event.InvoiceOverdueKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InvoiceOverdueConsumer {

  private static final Logger log = LoggerFactory.getLogger(InvoiceOverdueConsumer.class);
  private static final String EVENT_TYPE = "invoice.overdue";

  private final NotificationDispatcher notificationDispatcher;

  public InvoiceOverdueConsumer(NotificationDispatcher notificationDispatcher) {
    this.notificationDispatcher = notificationDispatcher;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "invoiceOverdueKafkaListenerContainerFactory")
  public void onInvoiceOverdue(InvoiceOverdueKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.invoiceId()));
    try {
      String dedupKey = payload.invoiceId() + ":" + payload.daysOverdue();
      log.info(
          "Received invoice.overdue: invoice={} daysOverdue={}",
          payload.invoiceId(),
          payload.daysOverdue());
      notificationDispatcher.dispatchInvoiceOverdue(EVENT_TYPE, dedupKey, payload);
    } finally {
      MDC.remove("requestId");
    }
  }
}
