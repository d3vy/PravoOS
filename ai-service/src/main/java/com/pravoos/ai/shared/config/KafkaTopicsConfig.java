package com.pravoos.ai.shared.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Value("${app.kafka.topic-partitions:3}")
    private int partitions;

    @Value("${app.kafka.topic-replicas:1}")
    private short replicas;

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic caseDeadlineApproachingTopic() {
        return topic("case.deadline.approaching");
    }

    @Bean
    public NewTopic caseHearingUpdatedTopic() {
        return topic("case.hearing.updated");
    }

    @Bean
    public NewTopic caseMessageCreatedTopic() {
        return topic("case.message.created");
    }

    @Bean
    public NewTopic lawyerDeletedDltTopic() {
        return topic("lawyer.deleted.DLT");
    }
}
