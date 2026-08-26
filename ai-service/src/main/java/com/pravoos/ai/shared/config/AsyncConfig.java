package com.pravoos.ai.shared.config;

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
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
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
  public ThreadPoolTaskExecutor tabularReviewExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
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
  public ThreadPoolTaskExecutor tabularReviewCellExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
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
  public ThreadPoolTaskExecutor chatStreamExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(8);
    executor.setMaxPoolSize(25);
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
  public Executor hybridSearchExecutor(HybridSearchProperties hybridSearchProperties) {
    if (!hybridSearchProperties.parallelEnabled()) {
      return Runnable::run;
    }
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
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
      @Value("${llm.context-parallel-enabled:true}") boolean contextParallelEnabled) {
    if (!contextParallelEnabled) {
      return Runnable::run;
    }
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(12);
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
      @Value("${search.parallel-enabled:true}") boolean searchParallelEnabled) {
    if (!searchParallelEnabled) {
      return Runnable::run;
    }
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(12);
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
      @Value("${dashboard.parallel-enabled:true}") boolean dashboardParallelEnabled) {
    if (!dashboardParallelEnabled) {
      return Runnable::run;
    }
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
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
