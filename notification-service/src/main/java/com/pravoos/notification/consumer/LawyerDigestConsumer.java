package com.pravoos.notification.consumer;

import com.pravoos.notification.event.LawyerDigestKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LawyerDigestConsumer {

  private static final Logger log = LoggerFactory.getLogger(LawyerDigestConsumer.class);
  private static final String EVENT_TYPE = "lawyer.digest.morning";

  private final NotificationDispatcher notificationDispatcher;
  private final ProcessedEventGuard processedEventGuard;

  public LawyerDigestConsumer(
      NotificationDispatcher notificationDispatcher, ProcessedEventGuard processedEventGuard) {
    this.notificationDispatcher = notificationDispatcher;
    this.processedEventGuard = processedEventGuard;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "lawyerDigestKafkaListenerContainerFactory")
  public void onMorningDigest(LawyerDigestKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.lawyerId()));
    try {
      String dedupKey = payload.lawyerId() + ":" + payload.digestDate();
      if (!processedEventGuard.claim(EVENT_TYPE, dedupKey)) {
        log.info("Skipping duplicate lawyer.digest.morning: {}", dedupKey);
        return;
      }
      log.info(
          "Received lawyer.digest.morning: lawyer={} date={}",
          payload.lawyerId(),
          payload.digestDate());
      try {
        notificationDispatcher.dispatchMorningDigest(payload);
      } catch (RuntimeException e) {
        processedEventGuard.release(EVENT_TYPE, dedupKey);
        throw e;
      }
    } finally {
      MDC.remove("requestId");
    }
  }
}
