package com.pravoos.ai.shared.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.shared.model.entity.OutboxEvent;
import com.pravoos.ai.shared.repository.jpa.OutboxEventRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

  @Mock private OutboxEventRepository outboxEventRepository;
  @Mock private KafkaTemplate<String, String> stringKafkaTemplate;

  private static final int MAX_ATTEMPTS = 10;

  private OutboxPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new OutboxPublisher(outboxEventRepository, stringKafkaTemplate, MAX_ATTEMPTS);
  }

  @Test
  void publishPending_doesNothingWhenNoPendingEvents() {
    when(outboxEventRepository.lockUnpublishedBatch(eq(MAX_ATTEMPTS), any())).thenReturn(List.of());

    publisher.publishPending();

    verify(stringKafkaTemplate, never()).send(any(String.class), any(), any());
  }

  @Test
  void publishPending_marksEventPublishedOnSuccessfulSend() {
    OutboxEvent event = new OutboxEvent("topic.a", "key-1", "{}");
    when(outboxEventRepository.lockUnpublishedBatch(eq(MAX_ATTEMPTS), any()))
        .thenReturn(List.of(event));
    when(stringKafkaTemplate.send("topic.a", "key-1", "{}"))
        .thenReturn(CompletableFuture.completedFuture(null));

    publisher.publishPending();

    assertThat(event.getPublishedAt()).isNotNull();
    assertThat(event.getAttempts()).isZero();
  }

  @Test
  void publishPending_incrementsAttemptsOnSendFailure() {
    OutboxEvent event = new OutboxEvent("topic.a", "key-1", "{}");
    when(outboxEventRepository.lockUnpublishedBatch(eq(MAX_ATTEMPTS), any()))
        .thenReturn(List.of(event));
    CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("broker unavailable"));
    when(stringKafkaTemplate.send("topic.a", "key-1", "{}")).thenReturn(failed);

    publisher.publishPending();

    assertThat(event.getPublishedAt()).isNull();
    assertThat(event.getAttempts()).isEqualTo(1);
  }

  @Test
  void publishPending_asksTheRepositoryToSkipEventsThatExhaustedTheirAttempts() {
    when(outboxEventRepository.lockUnpublishedBatch(eq(MAX_ATTEMPTS), any())).thenReturn(List.of());

    publisher.publishPending();

    verify(outboxEventRepository).lockUnpublishedBatch(eq(MAX_ATTEMPTS), any());
  }

  @Test
  void publishPending_publishesEachEventInBatchIndependently() {
    OutboxEvent succeeding = new OutboxEvent("topic.a", "key-1", "{}");
    OutboxEvent failing = new OutboxEvent("topic.b", "key-2", "{}");
    when(outboxEventRepository.lockUnpublishedBatch(eq(MAX_ATTEMPTS), any()))
        .thenReturn(List.of(succeeding, failing));
    when(stringKafkaTemplate.send("topic.a", "key-1", "{}"))
        .thenReturn(CompletableFuture.completedFuture(null));
    CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("broker unavailable"));
    when(stringKafkaTemplate.send("topic.b", "key-2", "{}")).thenReturn(failed);

    publisher.publishPending();

    assertThat(succeeding.getPublishedAt()).isNotNull();
    assertThat(failing.getPublishedAt()).isNull();
    assertThat(failing.getAttempts()).isEqualTo(1);
  }
}
