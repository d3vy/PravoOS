package com.pravoos.notification.consumer;

import com.pravoos.notification.event.NewLoginKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
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
  private final ProcessedEventGuard processedEventGuard;

  public NewLoginConsumer(
      NotificationDispatcher notificationDispatcher, ProcessedEventGuard processedEventGuard) {
    this.notificationDispatcher = notificationDispatcher;
    this.processedEventGuard = processedEventGuard;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "newLoginKafkaListenerContainerFactory")
  public void onNewLogin(NewLoginKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.userId()));
    try {
      String dedupKey = payload.userId() + ":" + payload.occurredAt() + ":" + payload.ipAddress();
      if (!processedEventGuard.claim(EVENT_TYPE, dedupKey)) {
        log.info("Skipping duplicate user.new_login: {}", dedupKey);
        return;
      }
      log.info("Received user.new_login: user={}", payload.userId());
      try {
        notificationDispatcher.dispatchNewLogin(payload);
      } catch (RuntimeException e) {
        processedEventGuard.release(EVENT_TYPE, dedupKey);
        throw e;
      }
    } finally {
      MDC.remove("requestId");
    }
  }
}
