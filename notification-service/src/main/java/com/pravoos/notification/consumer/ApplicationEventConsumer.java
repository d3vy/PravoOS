package com.pravoos.notification.consumer;

import com.pravoos.notification.event.ApplicationSubmittedKafkaPayload;
import com.pravoos.notification.service.ProcessedEventGuard;
import com.pravoos.notification.service.TelegramNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventConsumer.class);
    private static final String EVENT_TYPE = "application.submitted";

    private final TelegramNotificationService telegramNotificationService;
    private final ProcessedEventGuard processedEventGuard;

    public ApplicationEventConsumer(TelegramNotificationService telegramNotificationService,
                                    ProcessedEventGuard processedEventGuard) {
        this.telegramNotificationService = telegramNotificationService;
        this.processedEventGuard = processedEventGuard;
    }

    @KafkaListener(topics = EVENT_TYPE, groupId = "notification-service-group")
    public void onApplicationSubmitted(ApplicationSubmittedKafkaPayload payload) {
        MDC.put("requestId", String.valueOf(payload.applicationId()));
        try {
            String dedupKey = String.valueOf(payload.applicationId());
            if (processedEventGuard.isProcessed(EVENT_TYPE, dedupKey)) {
                log.info("Skipping duplicate application.submitted event: applicationId={}", payload.applicationId());
                return;
            }
            log.info("Received application.submitted event: applicationId={}", payload.applicationId());
            telegramNotificationService.notifyNewApplication(payload);
            processedEventGuard.markProcessed(EVENT_TYPE, dedupKey);
        } finally {
            MDC.remove("requestId");
        }
    }
}
