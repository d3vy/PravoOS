package com.pravoos.notification.consumer;

import com.pravoos.notification.event.ApplicationSubmittedKafkaPayload;
import com.pravoos.notification.service.TelegramNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ApplicationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventConsumer.class);
    private static final int PROCESSED_HISTORY_SIZE = 1000;

    private final TelegramNotificationService telegramNotificationService;
    private final Set<UUID> processedApplicationIds = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                    return size() > PROCESSED_HISTORY_SIZE;
                }
            }));

    public ApplicationEventConsumer(TelegramNotificationService telegramNotificationService) {
        this.telegramNotificationService = telegramNotificationService;
    }

    @KafkaListener(topics = "application.submitted", groupId = "notification-service-group")
    public void onApplicationSubmitted(ApplicationSubmittedKafkaPayload payload) {
        MDC.put("requestId", String.valueOf(payload.applicationId()));
        try {
            if (!processedApplicationIds.add(payload.applicationId())) {
                log.info("Skipping duplicate application.submitted event: applicationId={}", payload.applicationId());
                return;
            }
            log.info("Received application.submitted event: applicationId={}", payload.applicationId());
            telegramNotificationService.notifyNewApplication(payload);
        } finally {
            MDC.remove("requestId");
        }
    }
}
