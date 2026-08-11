package com.pravoos.notification.consumer;

import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
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

  public CaseHearingConsumer(NotificationDispatcher notificationDispatcher) {
    this.notificationDispatcher = notificationDispatcher;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "hearingKafkaListenerContainerFactory")
  public void onHearingUpdated(CaseHearingUpdatedKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.caseId()));
    try {
      String dedupKey = payload.caseId() + ":" + payload.newHearingDate();
      log.info(
          "Received case.hearing.updated: case={} {} -> {}",
          payload.caseId(),
          payload.previousHearingDate(),
          payload.newHearingDate());
      notificationDispatcher.dispatchHearingUpdate(EVENT_TYPE, dedupKey, payload);
    } finally {
      MDC.remove("requestId");
    }
  }
}
