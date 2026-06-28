package com.pravoos.notification.consumer;

import com.pravoos.notification.client.ApplicationDetailsResponse;
import com.pravoos.notification.client.UserServiceClient;
import com.pravoos.notification.event.ApplicationSubmittedEvent;
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
    private final UserServiceClient userServiceClient;
    private final ProcessedEventGuard processedEventGuard;

    public ApplicationEventConsumer(TelegramNotificationService telegramNotificationService,
                                    UserServiceClient userServiceClient,
                                    ProcessedEventGuard processedEventGuard) {
        this.telegramNotificationService = telegramNotificationService;
        this.userServiceClient = userServiceClient;
        this.processedEventGuard = processedEventGuard;
    }

    @KafkaListener(topics = EVENT_TYPE, groupId = "notification-service-group")
    public void onApplicationSubmitted(ApplicationSubmittedEvent event) {
        MDC.put("requestId", String.valueOf(event.applicationId()));
        try {
            String dedupKey = String.valueOf(event.applicationId());
            if (processedEventGuard.isProcessed(EVENT_TYPE, dedupKey)) {
                log.info("Skipping duplicate application.submitted event: applicationId={}", event.applicationId());
                return;
            }
            log.info("Received application.submitted event: applicationId={}", event.applicationId());

            ApplicationDetailsResponse details = userServiceClient.getApplication(event.applicationId()).orElse(null);
            if (details == null) {
                log.warn("Application {} no longer exists, skipping notification", event.applicationId());
                processedEventGuard.markProcessed(EVENT_TYPE, dedupKey);
                return;
            }

            telegramNotificationService.notifyNewApplication(new ApplicationSubmittedKafkaPayload(
                    event.applicationId(), details.fullName(), details.email(), details.specialization()));
            processedEventGuard.markProcessed(EVENT_TYPE, dedupKey);
        } finally {
            MDC.remove("requestId");
        }
    }
}
