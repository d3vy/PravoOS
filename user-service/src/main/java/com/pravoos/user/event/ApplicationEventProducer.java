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

    private final KafkaTemplate<String, ApplicationSubmittedKafkaPayload> kafkaTemplate;

    public ApplicationEventProducer(KafkaTemplate<String, ApplicationSubmittedKafkaPayload> kafkaTemplate) {
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
        kafkaTemplate.send(TOPIC_APPLICATION_SUBMITTED, application.getId().toString(), payload);
        log.info("Published application.submitted for: {}", application.getEmail());
    }
}
