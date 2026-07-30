package com.pravoos.notification.consumer;

import com.pravoos.notification.event.InvoiceOverdueKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
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
  private final ProcessedEventGuard processedEventGuard;

  public InvoiceOverdueConsumer(
      NotificationDispatcher notificationDispatcher, ProcessedEventGuard processedEventGuard) {
    this.notificationDispatcher = notificationDispatcher;
    this.processedEventGuard = processedEventGuard;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "invoiceOverdueKafkaListenerContainerFactory")
  public void onInvoiceOverdue(InvoiceOverdueKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.invoiceId()));
    try {
      String dedupKey = payload.invoiceId() + ":" + payload.daysOverdue();
      if (processedEventGuard.isProcessed(EVENT_TYPE, dedupKey)) {
        log.info("Skipping duplicate invoice.overdue: {}", dedupKey);
        return;
      }
      log.info(
          "Received invoice.overdue: invoice={} daysOverdue={}",
          payload.invoiceId(),
          payload.daysOverdue());
      notificationDispatcher.dispatchInvoiceOverdue(payload);
      processedEventGuard.markProcessed(EVENT_TYPE, dedupKey);
    } finally {
      MDC.remove("requestId");
    }
  }
}
