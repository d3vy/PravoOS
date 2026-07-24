package com.pravoos.notification.config;

import com.pravoos.notification.event.*;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

  private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);
  private static final long RETRY_INTERVAL_MS = 2000L;
  private static final long MAX_RETRIES = 3L;
  private static final int LISTENER_CONCURRENCY = 2;

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
  public ConcurrentKafkaListenerContainerFactory<String, ApplicationSubmittedEvent>
      kafkaListenerContainerFactory(KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
    return listenerFactory(ApplicationSubmittedEvent.class, deadLetterKafkaTemplate);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, CaseDeadlineKafkaPayload>
      deadlineKafkaListenerContainerFactory(KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
    return listenerFactory(CaseDeadlineKafkaPayload.class, deadLetterKafkaTemplate);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, CaseHearingUpdatedKafkaPayload>
      hearingKafkaListenerContainerFactory(KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
    return listenerFactory(CaseHearingUpdatedKafkaPayload.class, deadLetterKafkaTemplate);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, NewLoginKafkaPayload>
      newLoginKafkaListenerContainerFactory(KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
    return listenerFactory(NewLoginKafkaPayload.class, deadLetterKafkaTemplate);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, CaseMessageCreatedKafkaPayload>
      caseMessageKafkaListenerContainerFactory(
          KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
    return listenerFactory(CaseMessageCreatedKafkaPayload.class, deadLetterKafkaTemplate);
  }

  @Bean
  public ConsumerFactory<String, String> dltConsumerFactory() {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    return new DefaultKafkaConsumerFactory<>(
        props, new StringDeserializer(), new StringDeserializer());
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> dltKafkaListenerContainerFactory(
      ConsumerFactory<String, String> dltConsumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(dltConsumerFactory);
    return factory;
  }

  private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerFactory(
      Class<T> payloadType, KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
    ConcurrentKafkaListenerContainerFactory<String, T> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(jsonConsumerFactory(payloadType));
    factory.setCommonErrorHandler(errorHandler(deadLetterKafkaTemplate));
    factory.setConcurrency(LISTENER_CONCURRENCY);
    return factory;
  }

  private <T> ConsumerFactory<String, T> jsonConsumerFactory(Class<T> payloadType) {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));

    JsonDeserializer<T> jsonDeserializer = new JsonDeserializer<>(payloadType);
    jsonDeserializer.setUseTypeHeaders(false);
    jsonDeserializer.addTrustedPackages(payloadType.getPackageName());

    return new DefaultKafkaConsumerFactory<>(
        props,
        new ErrorHandlingDeserializer<>(new StringDeserializer()),
        new ErrorHandlingDeserializer<>(jsonDeserializer));
  }

  private DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate);
    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    errorHandler.setRetryListeners(
        (record, ex, deliveryAttempt) ->
            log.warn(
                "Kafka delivery attempt {} failed for topic {} offset {}: {}",
                deliveryAttempt,
                record.topic(),
                record.offset(),
                ex.getMessage()));
    return errorHandler;
  }
}
