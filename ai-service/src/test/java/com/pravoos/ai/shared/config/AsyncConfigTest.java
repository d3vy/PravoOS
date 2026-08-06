package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

  private final AsyncConfig config = new AsyncConfig();

  @Test
  void uncaughtExceptionHandlerLogsWithoutThrowing() throws NoSuchMethodException {
    assertThatNoException()
        .isThrownBy(
            () ->
                config
                    .getAsyncUncaughtExceptionHandler()
                    .handleUncaughtException(
                        new RuntimeException("boom"),
                        AsyncConfigTest.class.getDeclaredMethod(
                            "uncaughtExceptionHandlerLogsWithoutThrowing"),
                        "arg1"));
  }

  @Test
  void taskExecutorIsConfiguredForEmbeddingWork() {
    ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.taskExecutor();

    assertThat(executor.getCorePoolSize()).isEqualTo(2);
    assertThat(executor.getMaxPoolSize()).isEqualTo(4);
    assertThat(executor.getThreadNamePrefix()).isEqualTo("embedding-");
  }

  @Test
  void tabularReviewExecutorAbortsWhenSaturated() {
    ThreadPoolTaskExecutor executor = config.tabularReviewExecutor();

    assertThat(executor.getThreadNamePrefix()).isEqualTo("review-run-");
    assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
        .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
  }

  @Test
  void tabularReviewCellExecutorRunsOnCallerWhenSaturated() {
    ThreadPoolTaskExecutor executor = config.tabularReviewCellExecutor();

    assertThat(executor.getCorePoolSize()).isEqualTo(4);
    assertThat(executor.getThreadNamePrefix()).isEqualTo("review-doc-");
    assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
        .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
  }

  @Test
  void chatStreamExecutorIsConfiguredForHighConcurrency() {
    ThreadPoolTaskExecutor executor = config.chatStreamExecutor();

    assertThat(executor.getCorePoolSize()).isEqualTo(8);
    assertThat(executor.getMaxPoolSize()).isEqualTo(25);
    assertThat(executor.getThreadNamePrefix()).isEqualTo("chat-stream-");
  }
}
