package com.pravoos.llm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

  private final AsyncConfig asyncConfig = new AsyncConfig();

  @Test
  void streamExecutorRejectsTasksBeyondMaxPoolSizeInsteadOfQueueingThem() throws Exception {
    AsyncTaskExecutor executor = asyncConfig.llmStreamExecutor(true, 1, 1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(1);
    executor.execute(
        () -> {
          started.countDown();
          awaitQuietly(release);
        });
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

    try {
      assertThatThrownBy(() -> executor.execute(() -> {}))
          .isInstanceOf(RejectedExecutionException.class);
    } finally {
      release.countDown();
      ((ThreadPoolTaskExecutor) executor).shutdown();
    }
  }

  @Test
  void streamExecutorPropagatesLoggingContextToTheWorkerThread() throws Exception {
    AsyncTaskExecutor executor = asyncConfig.llmStreamExecutor(true, 1, 1);
    MDC.put("requestId", "req-42");
    CountDownLatch done = new CountDownLatch(1);
    StringBuilder observed = new StringBuilder();
    try {
      executor.execute(
          () -> {
            observed.append(MDC.get("requestId"));
            done.countDown();
          });
      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(observed.toString()).isEqualTo("req-42");
    } finally {
      MDC.clear();
      ((ThreadPoolTaskExecutor) executor).shutdown();
    }
  }

  @Test
  void disabledPoolFallsBackToThreadPerRequestExecution() throws Exception {
    AsyncTaskExecutor executor = asyncConfig.llmStreamExecutor(false, 1, 1);
    assertThat(executor).isNotInstanceOf(ThreadPoolTaskExecutor.class);

    CountDownLatch done = new CountDownLatch(2);
    executor.execute(done::countDown);
    executor.execute(done::countDown);
    assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
