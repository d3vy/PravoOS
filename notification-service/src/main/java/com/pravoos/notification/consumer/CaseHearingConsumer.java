package com.pravoos.notification.consumer;

import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.notification.service.TelegramNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CaseHearingConsumer {

    private static final Logger log = LoggerFactory.getLogger(CaseHearingConsumer.class);

    private final TelegramNotificationService telegramNotificationService;

    public CaseHearingConsumer(TelegramNotificationService telegramNotificationService) {
        this.telegramNotificationService = telegramNotificationService;
    }

    @KafkaListener(topics = "case.hearing.updated", groupId = "notification-service-group",
            containerFactory = "hearingKafkaListenerContainerFactory")
    public void onHearingUpdated(CaseHearingUpdatedKafkaPayload payload) {
        MDC.put("requestId", String.valueOf(payload.caseId()));
        try {
            log.info("Received case.hearing.updated: case={} {} -> {}",
                    payload.caseId(), payload.previousHearingDate(), payload.newHearingDate());
            telegramNotificationService.notifyHearingUpdated(payload);
        } finally {
            MDC.remove("requestId");
        }
    }
}
