package com.pravoos.notification.config;

import com.pravoos.notification.event.ApplicationSubmittedKafkaPayload;
import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
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
    public ProducerFactory<String, Object> deadLetterProducerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> deadLetterKafkaTemplate(
            ProducerFactory<String, Object> deadLetterProducerFactory) {
        return new KafkaTemplate<>(deadLetterProducerFactory);
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
            ConsumerFactory<String, ApplicationSubmittedKafkaPayload> consumerFactory,
            KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, ApplicationSubmittedKafkaPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler(deadLetterKafkaTemplate));
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
            ConsumerFactory<String, CaseDeadlineKafkaPayload> deadlineConsumerFactory,
            KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, CaseDeadlineKafkaPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(deadlineConsumerFactory);
        factory.setCommonErrorHandler(errorHandler(deadLetterKafkaTemplate));
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
            ConsumerFactory<String, CaseHearingUpdatedKafkaPayload> hearingConsumerFactory,
            KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, CaseHearingUpdatedKafkaPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(hearingConsumerFactory);
        factory.setCommonErrorHandler(errorHandler(deadLetterKafkaTemplate));
        return factory;
    }

    private DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate);
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Kafka delivery attempt {} failed for topic {} offset {}: {}",
                        deliveryAttempt, record.topic(), record.offset(), ex.getMessage()));
        return errorHandler;
    }
}
