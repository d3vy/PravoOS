package com.pravoos.notification.consumer;

import com.pravoos.notification.event.ApplicationSubmittedKafkaPayload;
import com.pravoos.notification.service.TelegramNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventConsumer.class);

    private final TelegramNotificationService telegramNotificationService;

    public ApplicationEventConsumer(TelegramNotificationService telegramNotificationService) {
        this.telegramNotificationService = telegramNotificationService;
    }

    @KafkaListener(topics = "application.submitted", groupId = "notification-service-group")
    public void onApplicationSubmitted(ApplicationSubmittedKafkaPayload payload) {
        log.info("Received application.submitted event: applicationId={}, email={}",
                payload.applicationId(), payload.email());
        telegramNotificationService.notifyNewApplication(payload);
    }
}
