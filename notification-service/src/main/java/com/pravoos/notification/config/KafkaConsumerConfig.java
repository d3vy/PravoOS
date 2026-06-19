package com.pravoos.notification.config;

import com.pravoos.notification.event.ApplicationSubmittedKafkaPayload;
import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);
    private static final long RETRY_INTERVAL_MS = 2000L;
    private static final long MAX_RETRIES = 3L;

    private final KafkaProperties kafkaProperties;

    public KafkaConsumerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ConsumerFactory<String, ApplicationSubmittedKafkaPayload> consumerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));

        JsonDeserializer<ApplicationSubmittedKafkaPayload> jsonDeserializer =
                new JsonDeserializer<>(ApplicationSubmittedKafkaPayload.class);
        jsonDeserializer.setUseTypeHeaders(false);
        jsonDeserializer.addTrustedPackages(ApplicationSubmittedKafkaPayload.class.getPackageName());

        ErrorHandlingDeserializer<ApplicationSubmittedKafkaPayload> valueDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ApplicationSubmittedKafkaPayload> kafkaListenerContainerFactory(
            ConsumerFactory<String, ApplicationSubmittedKafkaPayload> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, ApplicationSubmittedKafkaPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, CaseDeadlineKafkaPayload> deadlineConsumerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));

        JsonDeserializer<CaseDeadlineKafkaPayload> jsonDeserializer =
                new JsonDeserializer<>(CaseDeadlineKafkaPayload.class);
        jsonDeserializer.setUseTypeHeaders(false);
        jsonDeserializer.addTrustedPackages(CaseDeadlineKafkaPayload.class.getPackageName());

        ErrorHandlingDeserializer<CaseDeadlineKafkaPayload> valueDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CaseDeadlineKafkaPayload> deadlineKafkaListenerContainerFactory(
            ConsumerFactory<String, CaseDeadlineKafkaPayload> deadlineConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, CaseDeadlineKafkaPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(deadlineConsumerFactory);
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, CaseHearingUpdatedKafkaPayload> hearingConsumerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));

        JsonDeserializer<CaseHearingUpdatedKafkaPayload> jsonDeserializer =
                new JsonDeserializer<>(CaseHearingUpdatedKafkaPayload.class);
        jsonDeserializer.setUseTypeHeaders(false);
        jsonDeserializer.addTrustedPackages(CaseHearingUpdatedKafkaPayload.class.getPackageName());

        ErrorHandlingDeserializer<CaseHearingUpdatedKafkaPayload> valueDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CaseHearingUpdatedKafkaPayload> hearingKafkaListenerContainerFactory(
            ConsumerFactory<String, CaseHearingUpdatedKafkaPayload> hearingConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, CaseHearingUpdatedKafkaPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(hearingConsumerFactory);
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }

    private DefaultErrorHandler errorHandler() {
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Kafka delivery attempt {} failed for topic {} offset {}: {}",
                        deliveryAttempt, record.topic(), record.offset(), ex.getMessage()));
        return errorHandler;
    }
}
