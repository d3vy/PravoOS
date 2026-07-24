package com.pravoos.notification.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterConsumer {

  private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

  private final MeterRegistry meterRegistry;

  public DeadLetterConsumer(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @KafkaListener(
      topicPattern = ".*\\.DLT",
      groupId = "notification-dlt-group",
      containerFactory = "dltKafkaListenerContainerFactory")
  public void onDeadLetter(ConsumerRecord<String, String> record) {
    meterRegistry.counter("pravoos.kafka.dlt", "topic", record.topic()).increment();
    log.error(
        "DLT message received: topic={} key={} payload={}",
        record.topic(),
        record.key(),
        record.value());
  }
}
