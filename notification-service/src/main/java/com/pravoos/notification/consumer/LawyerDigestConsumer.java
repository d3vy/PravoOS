package com.pravoos.notification.consumer;

import com.pravoos.notification.event.LawyerDigestKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
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

  public LawyerDigestConsumer(NotificationDispatcher notificationDispatcher) {
    this.notificationDispatcher = notificationDispatcher;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "lawyerDigestKafkaListenerContainerFactory")
  public void onMorningDigest(LawyerDigestKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.lawyerId()));
    try {
      String dedupKey = payload.lawyerId() + ":" + payload.digestDate();
      log.info(
          "Received lawyer.digest.morning: lawyer={} date={}",
          payload.lawyerId(),
          payload.digestDate());
      notificationDispatcher.dispatchMorningDigest(EVENT_TYPE, dedupKey, payload);
    } finally {
      MDC.remove("requestId");
    }
  }
}
