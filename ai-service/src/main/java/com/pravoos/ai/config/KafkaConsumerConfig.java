package com.pravoos.ai.config;

import com.pravoos.ai.event.LawyerDeletedKafkaPayload;
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
    public ConsumerFactory<String, LawyerDeletedKafkaPayload> consumerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));

        JsonDeserializer<LawyerDeletedKafkaPayload> jsonDeserializer =
                new JsonDeserializer<>(LawyerDeletedKafkaPayload.class);
        jsonDeserializer.setUseTypeHeaders(false);
        jsonDeserializer.addTrustedPackages(LawyerDeletedKafkaPayload.class.getPackageName());

        ErrorHandlingDeserializer<LawyerDeletedKafkaPayload> valueDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LawyerDeletedKafkaPayload> kafkaListenerContainerFactory(
            ConsumerFactory<String, LawyerDeletedKafkaPayload> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, LawyerDeletedKafkaPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
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
