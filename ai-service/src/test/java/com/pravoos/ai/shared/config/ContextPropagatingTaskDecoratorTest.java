package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class ContextPropagatingTaskDecoratorTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  @Test
  void propagatesSecurityContextAndMdcToWorkerThread() throws InterruptedException {
    Authentication authentication =
        new UsernamePasswordAuthenticationToken("lawyer-42", null, java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
    MDC.put("correlationId", "corr-1");

    ThreadPoolTaskExecutor executor = executorWithDecorator();
    AtomicReference<Authentication> seenAuthentication = new AtomicReference<>();
    AtomicReference<String> seenCorrelationId = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);

    executor.execute(
        () -> {
          seenAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
          seenCorrelationId.set(MDC.get("correlationId"));
          done.countDown();
        });

    assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(seenAuthentication.get()).isSameAs(authentication);
    assertThat(seenCorrelationId.get()).isEqualTo("corr-1");
    executor.shutdown();
  }

  @Test
  void clearsWorkerThreadContextAfterTaskCompletes() throws InterruptedException {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("lawyer-42", null, java.util.List.of()));

    ThreadPoolTaskExecutor executor = executorWithDecorator();
    runAndAwait(executor, () -> {});

    SecurityContextHolder.clearContext();
    AtomicReference<Authentication> leaked = new AtomicReference<>();
    runAndAwait(executor, () -> leaked.set(SecurityContextHolder.getContext().getAuthentication()));

    assertThat(leaked.get()).isNull();
    executor.shutdown();
  }

  private void runAndAwait(ThreadPoolTaskExecutor executor, Runnable task)
      throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    executor.execute(
        () -> {
          try {
            task.run();
          } finally {
            done.countDown();
          }
        });
    assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
  }

  private ThreadPoolTaskExecutor executorWithDecorator() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.initialize();
    return executor;
  }
}
