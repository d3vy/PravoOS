package com.pravoos.notification.consumer;

import com.pravoos.notification.event.MailboxSyncPausedKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MailboxSyncPausedConsumer {

  private static final Logger log = LoggerFactory.getLogger(MailboxSyncPausedConsumer.class);
  private static final String EVENT_TYPE = "mailbox.sync.paused";

  private final NotificationDispatcher notificationDispatcher;

  public MailboxSyncPausedConsumer(NotificationDispatcher notificationDispatcher) {
    this.notificationDispatcher = notificationDispatcher;
  }

  @KafkaListener(
      topics = EVENT_TYPE,
      groupId = "notification-service-group",
      containerFactory = "mailboxSyncPausedKafkaListenerContainerFactory")
  public void onMailboxSyncPaused(MailboxSyncPausedKafkaPayload payload) {
    MDC.put("requestId", String.valueOf(payload.mailboxId()));
    try {
      String dedupKey = payload.mailboxId() + ":" + payload.pausedAt();
      log.info(
          "Received mailbox.sync.paused: mailbox={} failures={}",
          payload.mailboxId(),
          payload.failures());
      notificationDispatcher.dispatchMailboxPaused(EVENT_TYPE, dedupKey, payload);
    } finally {
      MDC.remove("requestId");
    }
  }
}
