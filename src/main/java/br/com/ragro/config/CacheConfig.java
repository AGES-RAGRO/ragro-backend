package br.com.ragro.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * In-memory Caffeine cache behind Spring's {@link CacheManager} (swap for Redis when multi-instance).
 * Only cache today is recommendations (per-customer LLM ranking, configurable TTL).
 */
@Configuration
@EnableCaching
@EnableAsync
public class CacheConfig {

  public static final String RECOMMENDATIONS_CACHE = "recommendations";

  @Bean
  public CacheManager cacheManager(
      @Value("${ragro.recommendations.cache.ttl-hours:6}") long ttlHours) {
    CaffeineCacheManager manager = new CaffeineCacheManager(RECOMMENDATIONS_CACHE);
    manager.setCaffeine(
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(ttlHours))
            .maximumSize(10_000)
            // recordStats enables cache.gets{result=hit|miss} for hit-rate metrics (E4).
            .recordStats());
    return manager;
  }

  /**
   * Executor for async recommendation rerank. Kept small (2 threads): warming caches without
   * competing with Tomcat; the request already responded with the heuristic.
   */
  @Bean(name = "recommendationExecutor")
  public Executor recommendationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("rec-warmup-");
    // Warm-up is best-effort: discard under saturation instead of throwing RejectedExecutionException.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
    executor.initialize();
    return executor;
  }
}
