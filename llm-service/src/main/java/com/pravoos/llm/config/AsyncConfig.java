package com.pravoos.llm.config;

import com.pravoos.common.web.ContextPropagatingTaskDecorator;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

  @Bean(name = "llmStreamExecutor")
  public AsyncTaskExecutor llmStreamExecutor(
      @Value("${llm.stream.pool-enabled:true}") boolean poolEnabled,
      @Value("${async.llm-stream.core-pool-size:6}") int corePoolSize,
      @Value("${async.llm-stream.max-pool-size:16}") int maxPoolSize) {
    if (!poolEnabled) {
      SimpleAsyncTaskExecutor fallback = new SimpleAsyncTaskExecutor("llm-stream-");
      fallback.setTaskDecorator(new ContextPropagatingTaskDecorator());
      return fallback;
    }
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("llm-stream-");
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
