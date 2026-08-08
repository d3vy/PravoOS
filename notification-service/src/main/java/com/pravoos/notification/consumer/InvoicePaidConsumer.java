package com.pravoos.notification.consumer;

import com.pravoos.notification.event.InvoicePaidKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
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
  private final ProcessedEventGuard processedEventGuard;

  public InvoicePaidConsumer(
      NotificationDispatcher notificationDispatcher, ProcessedEventGuard processedEventGuard) {
    this.notificationDispatcher = notificationDispatcher;
    this.processedEventGuard = processedEventGuard;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "invoicePaidKafkaListenerContainerFactory")
  public void onInvoicePaid(InvoicePaidKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.invoiceId()));
    try {
      String dedupKey = String.valueOf(payload.invoiceId());
      if (!processedEventGuard.claim(EVENT_TYPE, dedupKey)) {
        log.info("Skipping duplicate invoice.paid: {}", dedupKey);
        return;
      }
      log.info("Received invoice.paid: invoice={}", payload.invoiceId());
      try {
        notificationDispatcher.dispatchInvoicePaid(payload);
      } catch (RuntimeException e) {
        processedEventGuard.release(EVENT_TYPE, dedupKey);
        throw e;
      }
    } finally {
      MDC.remove("requestId");
    }
  }
}
