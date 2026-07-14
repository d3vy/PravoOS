package com.pravoos.notification.consumer;

import com.pravoos.notification.event.CaseMessageCreatedKafkaPayload;
import com.pravoos.notification.service.ProcessedEventGuard;
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
    private final ProcessedEventGuard processedEventGuard;

    public CaseMessageConsumer(NotificationDispatcher notificationDispatcher,
                               ProcessedEventGuard processedEventGuard) {
        this.notificationDispatcher = notificationDispatcher;
        this.processedEventGuard = processedEventGuard;
    }

    @KafkaListener(topics = EVENT_TYPE, groupId = "notification-service-group",
            containerFactory = "caseMessageKafkaListenerContainerFactory")
    public void onCaseMessageCreated(CaseMessageCreatedKafkaPayload payload) {
        MDC.put("requestId", String.valueOf(payload.messageId()));
        try {
            String dedupKey = String.valueOf(payload.messageId());
            if (processedEventGuard.isProcessed(EVENT_TYPE, dedupKey)) {
                log.info("Skipping duplicate case.message.created: {}", dedupKey);
                return;
            }
            log.info("Received case.message.created: case={} message={} author={}",
                    payload.caseId(), payload.messageId(), payload.authorRole());
            notificationDispatcher.dispatchCaseMessage(payload);
            processedEventGuard.markProcessed(EVENT_TYPE, dedupKey);
        } finally {
            MDC.remove("requestId");
        }
    }
}
