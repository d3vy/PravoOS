package com.pravoos.notification.consumer;

import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CaseDeadlineConsumer {

  private static final Logger log = LoggerFactory.getLogger(CaseDeadlineConsumer.class);
  private static final String EVENT_TYPE = "case.deadline.approaching";

  private final NotificationDispatcher notificationDispatcher;
  private final ProcessedEventGuard processedEventGuard;

  public CaseDeadlineConsumer(
      NotificationDispatcher notificationDispatcher, ProcessedEventGuard processedEventGuard) {
    this.notificationDispatcher = notificationDispatcher;
    this.processedEventGuard = processedEventGuard;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "deadlineKafkaListenerContainerFactory")
  public void onDeadlineApproaching(CaseDeadlineKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.caseId()));
    try {
      String dedupKey =
          payload.caseId() + ":" + payload.deadlineTypeName() + ":" + payload.daysLeft();
      if (processedEventGuard.isProcessed(EVENT_TYPE, dedupKey)) {
        log.info("Skipping duplicate case.deadline.approaching: {}", dedupKey);
        return;
      }
      log.info(
          "Received case.deadline.approaching: case={} type={} daysLeft={}",
          payload.caseId(),
          payload.deadlineTypeName(),
          payload.daysLeft());
      notificationDispatcher.dispatchDeadline(payload);
      processedEventGuard.markProcessed(EVENT_TYPE, dedupKey);
    } finally {
      MDC.remove("requestId");
    }
  }
}
