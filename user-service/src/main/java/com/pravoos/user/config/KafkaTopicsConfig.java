package com.pravoos.user.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic applicationSubmittedTopic() {
        return topic("application.submitted");
    }

    @Bean
    public NewTopic lawyerDeletedTopic() {
        return topic("lawyer.deleted");
    }
}
