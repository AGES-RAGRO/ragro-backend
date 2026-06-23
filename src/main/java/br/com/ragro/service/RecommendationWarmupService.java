package br.com.ragro.service;

import br.com.ragro.config.CacheConfig;
import br.com.ragro.domain.enums.RecommendationReason;
import br.com.ragro.domain.event.OrderStatusChangedEvent;
import br.com.ragro.domain.llm.Candidate;
import br.com.ragro.domain.llm.CustomerFeatures;
import br.com.ragro.domain.llm.RankedRecommendation;
import br.com.ragro.service.api.LlmRerankerPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Owns the per-customer recommendation cache. The LLM rerank runs here asynchronously and warms the
 * cache, so the LLM is called at most 1× per customer per TTL window (or after a new order), not per
 * screen open.
 */
@Service
@RequiredArgsConstructor
public class RecommendationWarmupService {

  private static final Logger log = LoggerFactory.getLogger(RecommendationWarmupService.class);

  private final LlmRerankerPort llmRerankerPort;
  private final CacheManager cacheManager;
  private final MeterRegistry meterRegistry;

  /** Prevents duplicate warm-ups for the same customer while one is already in progress. */
  private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

  /** The customer's cached ranking, or {@code null} on a cold/expired cache. */
  @SuppressWarnings("unchecked")
  public List<RankedRecommendation> getCached(UUID customerId) {
    Cache cache = cacheManager.getCache(CacheConfig.RECOMMENDATIONS_CACHE);
    if (cache == null) {
      return null;
    }
    return cache.get(customerId, List.class);
  }

  /**
   * Reranks candidates with the LLM off the request thread and caches the result. On failure/invalid output,
   * caches the heuristic ranking so the customer is never left without recommendations.
   */
  @Async("recommendationExecutor")
  public void warmAsync(
      UUID customerId,
      List<Candidate> candidates,
      CustomerFeatures features,
      List<RankedRecommendation> heuristicRanked) {
    if (!inFlight.add(customerId)) {
      return;
    }
    try {
      Set<UUID> candidateIds =
          candidates.stream().map(Candidate::getProductId).collect(Collectors.toSet());
      List<RankedRecommendation> ranked =
          llmRerankerPort.rerank(candidates, features).stream()
              .filter(item -> candidateIds.contains(item.getProductId()))
              .sorted(Comparator.comparingDouble(item -> -item.getScore()))
              .map(
                  item ->
                      new RankedRecommendation(
                          item.getProductId(),
                          (int) (item.getScore() * 100),
                          RecommendationReason.LLM_RERANKED))
              .toList();
      if (ranked.isEmpty()) {
        put(customerId, heuristicRanked);
        meterRegistry
            .counter("ragro.recommendation.rerank", "outcome", "fallback", "reason", "empty")
            .increment();
        return;
      }
      put(customerId, ranked);
      meterRegistry.counter("ragro.recommendation.rerank", "outcome", "ai").increment();
    } catch (Exception e) {
      log.warn("Async LLM rerank failed for customer {}: {}", customerId, e.getMessage());
      put(customerId, heuristicRanked);
      meterRegistry
          .counter("ragro.recommendation.rerank", "outcome", "fallback", "reason", "error")
          .increment();
    } finally {
      inFlight.remove(customerId);
    }
  }

  /** Order state changes alter the customer's profile, so evict on them instead of waiting for the TTL. */
  @EventListener
  public void evictOnOrderChange(OrderStatusChangedEvent event) {
    Cache cache = cacheManager.getCache(CacheConfig.RECOMMENDATIONS_CACHE);
    if (cache != null) {
      cache.evict(event.order().getCustomer().getId());
    }
  }

  private void put(UUID customerId, List<RankedRecommendation> ranked) {
    Cache cache = cacheManager.getCache(CacheConfig.RECOMMENDATIONS_CACHE);
    if (cache != null) {
      cache.put(customerId, ranked);
    }
  }
}
