package br.com.ragro.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Cache in-memory (Caffeine) atrás da abstração Spring Cache — trocar para Redis na etapa de
 * múltiplas instâncias é só substituir o {@link CacheManager}. Hoje o único cache é o de
 * recomendações (ranking LLM por cliente, TTL configurável).
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
            // Sem isto o Micrometer só registra cache.size; recordStats habilita
            // cache.gets{result=hit|miss}, de onde sai o hit-rate (meta do E4).
            .recordStats());
    return manager;
  }

  /**
   * Executor dedicado do rerank assíncrono de recomendações. Pequeno de propósito: a chamada à
   * NVIDIA demora segundos e 2 threads bastam para aquecer caches sem competir com o Tomcat; em
   * pico, warm-ups excedentes ficam na fila (e a request já respondeu com a heurística).
   */
  @Bean(name = "recommendationExecutor")
  public Executor recommendationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("rec-warmup-");
    executor.initialize();
    return executor;
  }
}
