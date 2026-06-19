package com.pravoos.notification.consumer;

import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.service.TelegramNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CaseDeadlineConsumer {

    private static final Logger log = LoggerFactory.getLogger(CaseDeadlineConsumer.class);

    private final TelegramNotificationService telegramNotificationService;

    public CaseDeadlineConsumer(TelegramNotificationService telegramNotificationService) {
        this.telegramNotificationService = telegramNotificationService;
    }

    @KafkaListener(topics = "case.deadline.approaching", groupId = "notification-service-group",
            containerFactory = "deadlineKafkaListenerContainerFactory")
    public void onDeadlineApproaching(CaseDeadlineKafkaPayload payload) {
        MDC.put("requestId", String.valueOf(payload.caseId()));
        try {
            log.info("Received case.deadline.approaching: case={} type={} daysLeft={}",
                    payload.caseId(), payload.deadlineTypeName(), payload.daysLeft());
            telegramNotificationService.notifyDeadline(payload);
        } finally {
            MDC.remove("requestId");
        }
    }
}
