package com.pravoos.notification.consumer;

import com.pravoos.notification.event.CaseMessageCreatedKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CaseMessageConsumer {

  private static final Logger log = LoggerFactory.getLogger(CaseMessageConsumer.class);
  private static final String EVENT_TYPE = "case.message.created";

  private final NotificationDispatcher notificationDispatcher;

  public CaseMessageConsumer(NotificationDispatcher notificationDispatcher) {
    this.notificationDispatcher = notificationDispatcher;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "caseMessageKafkaListenerContainerFactory")
  public void onCaseMessageCreated(CaseMessageCreatedKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.messageId()));
    try {
      String dedupKey = String.valueOf(payload.messageId());
      log.info(
          "Received case.message.created: case={} message={} author={}",
          payload.caseId(),
          payload.messageId(),
          payload.authorRole());
      notificationDispatcher.dispatchCaseMessage(EVENT_TYPE, dedupKey, payload);
    } finally {
      MDC.remove("requestId");
    }
  }
}
