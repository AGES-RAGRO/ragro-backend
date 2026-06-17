package br.com.ragro.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Enables {@code @Async} and provides a bounded executor for off-request-thread push delivery. */
@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "pushNotificationExecutor")
  public Executor pushNotificationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("fcm-push-");
    executor.initialize();
    return executor;
  }
}
