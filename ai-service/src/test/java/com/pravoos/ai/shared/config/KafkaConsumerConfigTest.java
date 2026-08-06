package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pravoos.ai.shared.event.LawyerDeletedKafkaPayload;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

class KafkaConsumerConfigTest {

  private final KafkaConsumerConfig config = new KafkaConsumerConfig(new KafkaProperties(), 4);

  @Test
  void consumerFactoryUsesErrorHandlingDeserializers() {
    ConsumerFactory<String, LawyerDeletedKafkaPayload> factory = config.consumerFactory();

    assertThat(factory).isInstanceOf(DefaultKafkaConsumerFactory.class);
    assertThat(factory.getKeyDeserializer()).isInstanceOf(ErrorHandlingDeserializer.class);
    assertThat(factory.getValueDeserializer()).isInstanceOf(ErrorHandlingDeserializer.class);
  }

  @Test
  void listenerContainerFactoryUsesConfiguredConcurrencyAndConsumerFactory() {
    ConsumerFactory<String, LawyerDeletedKafkaPayload> consumerFactory = config.consumerFactory();
    @SuppressWarnings("unchecked")
    KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);

    ConcurrentKafkaListenerContainerFactory<String, LawyerDeletedKafkaPayload> containerFactory =
        config.kafkaListenerContainerFactory(consumerFactory, kafkaTemplate);

    assertThat(containerFactory.getConsumerFactory()).isSameAs(consumerFactory);
    assertThat(containerFactory.getContainerProperties()).isNotNull();
  }
}
