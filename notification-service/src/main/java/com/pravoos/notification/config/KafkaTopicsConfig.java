package com.pravoos.notification.config;

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
  public NewTopic applicationSubmittedDltTopic() {
    return topic("application.submitted.DLT");
  }

  @Bean
  public NewTopic caseDeadlineApproachingDltTopic() {
    return topic("case.deadline.approaching.DLT");
  }

  @Bean
  public NewTopic caseHearingUpdatedDltTopic() {
    return topic("case.hearing.updated.DLT");
  }

  @Bean
  public NewTopic caseMessageCreatedDltTopic() {
    return topic("case.message.created.DLT");
  }

  @Bean
  public NewTopic invoiceOverdueDltTopic() {
    return topic("invoice.overdue.DLT");
  }

  @Bean
  public NewTopic invoicePaidDltTopic() {
    return topic("invoice.paid.DLT");
  }

  @Bean
  public NewTopic lawyerDigestMorningDltTopic() {
    return topic("lawyer.digest.morning.DLT");
  }

  @Bean
  public NewTopic newLoginDltTopic() {
    return topic("user.new_login.DLT");
  }
}
