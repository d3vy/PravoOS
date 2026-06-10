package com.pravoos.user.event;

import com.pravoos.user.model.entity.LawyerApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ApplicationEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventProducer.class);
    private static final String TOPIC_APPLICATION_SUBMITTED = "application.submitted";
    private static final String TOPIC_LAWYER_DELETED = "lawyer.deleted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ApplicationEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationSubmitted(ApplicationSubmittedSpringEvent event) {
        LawyerApplication application = event.application();
        ApplicationSubmittedKafkaPayload payload = new ApplicationSubmittedKafkaPayload(
                application.getId(),
                application.getFullName(),
                application.getEmail(),
                application.getSpecialization()
        );
        String key = application.getId().toString();
        kafkaTemplate.send(TOPIC_APPLICATION_SUBMITTED, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish application.submitted for {}: {}",
                                application.getEmail(), ex.getMessage(), ex);
                    } else {
                        log.info("Published application.submitted for: {}", application.getEmail());
                    }
                });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLawyerDeleted(LawyerDeletedSpringEvent event) {
        String key = event.userId().toString();
        kafkaTemplate.send(TOPIC_LAWYER_DELETED, key, new LawyerDeletedKafkaPayload(event.userId()))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish lawyer.deleted for {}: {}", key, ex.getMessage(), ex);
                    } else {
                        log.info("Published lawyer.deleted for: {}", key);
                    }
                });
    }
}
