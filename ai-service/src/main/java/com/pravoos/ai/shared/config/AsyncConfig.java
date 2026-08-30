package com.pravoos.ai.shared.config;

import com.pravoos.common.web.ContextPropagatingTaskDecorator;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (ex, method, params) ->
        log.error(
            "Uncaught exception in async method {} with args {}",
            method,
            Arrays.toString(params),
            ex);
  }

  @Bean(name = "taskExecutor")
  public Executor taskExecutor(
      @Value("${async.embedding.core-pool-size:2}") int corePoolSize,
      @Value("${async.embedding.max-pool-size:4}") int maxPoolSize) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("embedding-");
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();
    return executor;
  }

  @Bean(name = "tabularReviewExecutor")
  public ThreadPoolTaskExecutor tabularReviewExecutor(
      @Value("${async.tabular-review.core-pool-size:2}") int corePoolSize,
      @Value("${async.tabular-review.max-pool-size:4}") int maxPoolSize) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("review-run-");
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();
    return executor;
  }

  @Bean(name = "tabularReviewCellExecutor")
  public ThreadPoolTaskExecutor tabularReviewCellExecutor(
      @Value("${async.tabular-review-cell.core-pool-size:4}") int corePoolSize,
      @Value("${async.tabular-review-cell.max-pool-size:8}") int maxPoolSize) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("review-doc-");
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();
    return executor;
  }

  @Bean(name = "chatStreamExecutor")
  public ThreadPoolTaskExecutor chatStreamExecutor(
      @Value("${async.chat-stream.core-pool-size:6}") int corePoolSize,
      @Value("${async.chat-stream.max-pool-size:16}") int maxPoolSize) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("chat-stream-");
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }

  @Bean(name = "hybridSearchExecutor")
  public Executor hybridSearchExecutor(
      HybridSearchProperties hybridSearchProperties,
      @Value("${async.hybrid-search.core-pool-size:4}") int corePoolSize,
      @Value("${async.hybrid-search.max-pool-size:8}") int maxPoolSize) {
    if (!hybridSearchProperties.parallelEnabled()) {
      return Runnable::run;
    }
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("hybrid-search-");
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(15);
    executor.initialize();
    return executor;
  }

  @Bean(name = "chatContextExecutor")
  public Executor chatContextExecutor(
      @Value("${llm.context-parallel-enabled:true}") boolean contextParallelEnabled,
      @Value("${async.chat-context.core-pool-size:4}") int corePoolSize,
      @Value("${async.chat-context.max-pool-size:8}") int maxPoolSize) {
    if (!contextParallelEnabled) {
      return Runnable::run;
    }
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("chat-context-");
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }

  @Bean(name = "globalSearchExecutor")
  public Executor globalSearchExecutor(
      @Value("${search.parallel-enabled:true}") boolean searchParallelEnabled,
      @Value("${async.global-search.core-pool-size:4}") int corePoolSize,
      @Value("${async.global-search.max-pool-size:8}") int maxPoolSize) {
    if (!searchParallelEnabled) {
      return Runnable::run;
    }
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("global-search-");
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(15);
    executor.initialize();
    return executor;
  }

  @Bean(name = "dashboardExecutor")
  public Executor dashboardExecutor(
      @Value("${dashboard.parallel-enabled:true}") boolean dashboardParallelEnabled,
      @Value("${async.dashboard.core-pool-size:4}") int corePoolSize,
      @Value("${async.dashboard.max-pool-size:8}") int maxPoolSize) {
    if (!dashboardParallelEnabled) {
      return Runnable::run;
    }
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("dashboard-");
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(15);
    executor.initialize();
    return executor;
  }
}
