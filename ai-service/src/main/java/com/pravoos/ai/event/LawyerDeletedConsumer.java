package com.pravoos.ai.event;

import com.pravoos.ai.service.LawyerDataCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LawyerDeletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(LawyerDeletedConsumer.class);

    private final LawyerDataCleanupService lawyerDataCleanupService;

    public LawyerDeletedConsumer(LawyerDataCleanupService lawyerDataCleanupService) {
        this.lawyerDataCleanupService = lawyerDataCleanupService;
    }

    @KafkaListener(topics = "lawyer.deleted", groupId = "ai-service-group")
    public void onLawyerDeleted(LawyerDeletedKafkaPayload payload) {
        if (payload == null || payload.userId() == null) {
            log.warn("Received lawyer.deleted with null payload, skipping");
            return;
        }
        log.info("Received lawyer.deleted for {}", payload.userId());
        lawyerDataCleanupService.purgeLawyerData(payload.userId(), payload.orgCaseOwners());
    }
}
