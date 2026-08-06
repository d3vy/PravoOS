package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class KafkaTopicsConfigTest {

  private final KafkaTopicsConfig config = new KafkaTopicsConfig();

  private KafkaTopicsConfig configured() {
    ReflectionTestUtils.setField(config, "partitions", 5);
    ReflectionTestUtils.setField(config, "replicas", (short) 2);
    return config;
  }

  @Test
  void caseDeadlineApproachingTopicUsesConfiguredPartitionsAndReplicas() {
    NewTopic topic = configured().caseDeadlineApproachingTopic();

    assertThat(topic.name()).isEqualTo("case.deadline.approaching");
    assertThat(topic.numPartitions()).isEqualTo(5);
    assertThat(topic.replicationFactor()).isEqualTo((short) 2);
  }

  @Test
  void caseHearingUpdatedTopicHasExpectedName() {
    NewTopic topic = configured().caseHearingUpdatedTopic();

    assertThat(topic.name()).isEqualTo("case.hearing.updated");
  }

  @Test
  void caseMessageCreatedTopicHasExpectedName() {
    NewTopic topic = configured().caseMessageCreatedTopic();

    assertThat(topic.name()).isEqualTo("case.message.created");
  }

  @Test
  void lawyerDeletedDltTopicHasExpectedName() {
    NewTopic topic = configured().lawyerDeletedDltTopic();

    assertThat(topic.name()).isEqualTo("lawyer.deleted.DLT");
  }
}
