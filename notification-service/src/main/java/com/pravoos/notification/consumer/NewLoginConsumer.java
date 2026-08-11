package com.pravoos.notification.consumer;

import com.pravoos.notification.event.NewLoginKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NewLoginConsumer {

  private static final Logger log = LoggerFactory.getLogger(NewLoginConsumer.class);
  private static final String EVENT_TYPE = "user.new_login";

  private final NotificationDispatcher notificationDispatcher;

  public NewLoginConsumer(NotificationDispatcher notificationDispatcher) {
    this.notificationDispatcher = notificationDispatcher;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "newLoginKafkaListenerContainerFactory")
  public void onNewLogin(NewLoginKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.userId()));
    try {
      String dedupKey = payload.userId() + ":" + payload.occurredAt() + ":" + payload.ipAddress();
      log.info("Received user.new_login: user={}", payload.userId());
      notificationDispatcher.dispatchNewLogin(EVENT_TYPE, dedupKey, payload);
    } finally {
      MDC.remove("requestId");
    }
  }
}
