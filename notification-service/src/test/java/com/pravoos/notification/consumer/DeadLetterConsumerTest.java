package com.pravoos.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class DeadLetterConsumerTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final DeadLetterConsumer consumer = new DeadLetterConsumer(meterRegistry);

  @Test
  void onDeadLetter_incrementsCounterTaggedByTopic() {
    ConsumerRecord<String, String> record =
        new ConsumerRecord<>("invoice.paid.DLT", 0, 0L, "key-1", "{\"broken\":true}");

    consumer.onDeadLetter(record);

    assertThat(meterRegistry.counter("pravoos.kafka.dlt", "topic", "invoice.paid.DLT").count())
        .isEqualTo(1.0);
  }

  @Test
  void onDeadLetter_countsSeparatelyPerTopic() {
    consumer.onDeadLetter(new ConsumerRecord<>("invoice.paid.DLT", 0, 0L, "k1", "v1"));
    consumer.onDeadLetter(new ConsumerRecord<>("invoice.paid.DLT", 0, 1L, "k2", "v2"));
    consumer.onDeadLetter(new ConsumerRecord<>("case.deadline.approaching.DLT", 0, 0L, "k3", "v3"));

    assertThat(meterRegistry.counter("pravoos.kafka.dlt", "topic", "invoice.paid.DLT").count())
        .isEqualTo(2.0);
    assertThat(
            meterRegistry
                .counter("pravoos.kafka.dlt", "topic", "case.deadline.approaching.DLT")
                .count())
        .isEqualTo(1.0);
  }
}
