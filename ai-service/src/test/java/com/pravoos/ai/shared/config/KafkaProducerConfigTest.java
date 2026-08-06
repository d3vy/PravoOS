package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

class KafkaProducerConfigTest {

  private final KafkaProducerConfig config = new KafkaProducerConfig(new KafkaProperties());

  @Test
  void stringProducerFactoryEnablesIdempotenceAndAcksAll() {
    ProducerFactory<String, String> factory = config.stringProducerFactory();

    assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);
    var producerFactory = (DefaultKafkaProducerFactory<String, String>) factory;
    assertThat(producerFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
        .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
        .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
        .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
  }

  @Test
  void stringKafkaTemplateWrapsProvidedProducerFactory() {
    ProducerFactory<String, String> factory = config.stringProducerFactory();

    KafkaTemplate<String, String> template = config.stringKafkaTemplate(factory);

    assertThat(template.getProducerFactory()).isSameAs(factory);
  }
}
