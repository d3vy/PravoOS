package com.pravoos.notification.consumer;

import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CaseHearingConsumer {

  private static final Logger log = LoggerFactory.getLogger(CaseHearingConsumer.class);
  private static final String EVENT_TYPE = "case.hearing.updated";

  private final NotificationDispatcher notificationDispatcher;
  private final ProcessedEventGuard processedEventGuard;

  public CaseHearingConsumer(
      NotificationDispatcher notificationDispatcher, ProcessedEventGuard processedEventGuard) {
    this.notificationDispatcher = notificationDispatcher;
    this.processedEventGuard = processedEventGuard;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "hearingKafkaListenerContainerFactory")
  public void onHearingUpdated(CaseHearingUpdatedKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.caseId()));
    try {
      String dedupKey = payload.caseId() + ":" + payload.newHearingDate();
      if (processedEventGuard.isProcessed(EVENT_TYPE, dedupKey)) {
        log.info("Skipping duplicate case.hearing.updated: {}", dedupKey);
        return;
      }
      log.info(
          "Received case.hearing.updated: case={} {} -> {}",
          payload.caseId(),
          payload.previousHearingDate(),
          payload.newHearingDate());
      notificationDispatcher.dispatchHearingUpdate(payload);
      processedEventGuard.markProcessed(EVENT_TYPE, dedupKey);
    } finally {
      MDC.remove("requestId");
    }
  }
}
