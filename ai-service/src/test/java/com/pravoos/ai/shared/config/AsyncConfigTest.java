package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

  private final AsyncConfig config = new AsyncConfig();

  @Test
  void chatStreamExecutorRejectsInsteadOfQueueingWhenSaturated() throws InterruptedException {
    ThreadPoolTaskExecutor executor = config.chatStreamExecutor();
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(executor.getMaxPoolSize());
    try {
      for (int i = 0; i < executor.getMaxPoolSize(); i++) {
        executor.execute(
            () -> {
              started.countDown();
              awaitQuietly(release);
            });
      }
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> executor.execute(() -> {}))
          .isInstanceOf(TaskRejectedException.class);
    } finally {
      release.countDown();
      executor.shutdown();
    }
  }

  @Test
  void backgroundExecutorsKeepQueueingInsteadOfRejecting() {
    assertThat(config.tabularReviewExecutor().getQueueCapacity()).isPositive();
    assertThat(config.tabularReviewCellExecutor().getQueueCapacity()).isPositive();
  }

  private void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
