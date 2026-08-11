package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.event.MailboxSyncPausedPayload;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.shared.service.OutboxEventService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class MailboxAlertPublisher {

  private static final String SYNC_PAUSED_TOPIC = "mailbox.sync.paused";

  private final OutboxEventService outboxEventService;

  public MailboxAlertPublisher(OutboxEventService outboxEventService) {
    this.outboxEventService = outboxEventService;
  }

  public void enqueuePaused(Mailbox mailbox, LocalDateTime pausedAt) {
    outboxEventService.enqueue(
        SYNC_PAUSED_TOPIC,
        mailbox.getId().toString(),
        new MailboxSyncPausedPayload(
            mailbox.getId(),
            mailbox.getUserId(),
            mailbox.getEmailAddress(),
            mailbox.getLastError(),
            mailbox.getConsecutiveFailures(),
            pausedAt
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
  }
}
