package com.pravoos.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ClassUtils;

class KafkaTopicsConfigTest {

  private static final String CONSUMER_PACKAGE = "com.pravoos.notification.consumer";

  private Set<String> declaredTopics() {
    KafkaTopicsConfig config = new KafkaTopicsConfig();
    ReflectionTestUtils.setField(config, "partitions", 3);
    ReflectionTestUtils.setField(config, "replicas", (short) 1);

    Set<String> topics = new LinkedHashSet<>();
    for (Method method : KafkaTopicsConfig.class.getDeclaredMethods()) {
      if (method.isAnnotationPresent(Bean.class)) {
        topics.add(((NewTopic) ReflectionTestUtils.invokeMethod(config, method.getName())).name());
      }
    }
    return topics;
  }

  private Set<String> consumedTopics() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

    Set<String> topics = new LinkedHashSet<>();
    for (BeanDefinition definition : scanner.findCandidateComponents(CONSUMER_PACKAGE)) {
      Class<?> consumer =
          ClassUtils.resolveClassName(definition.getBeanClassName(), getClass().getClassLoader());
      for (Method method : consumer.getDeclaredMethods()) {
        KafkaListener listener = method.getAnnotation(KafkaListener.class);
        if (listener != null) {
          topics.addAll(Arrays.asList(listener.topics()));
        }
      }
    }
    return topics;
  }

  @Test
  void everyConsumedTopicHasADeadLetterTopicDeclared() {
    Set<String> declared = declaredTopics();
    Set<String> consumed = consumedTopics();

    assertThat(consumed).isNotEmpty();
    assertThat(consumed.stream().map(topic -> topic + ".DLT").toList())
        .allSatisfy(dlt -> assertThat(declared).contains(dlt));
  }

  @Test
  void declaresOnlyDeadLetterTopicsBecauseNotificationServiceProducesNoEvents() {
    assertThat(declaredTopics()).allSatisfy(topic -> assertThat(topic).endsWith(".DLT"));
  }
}
