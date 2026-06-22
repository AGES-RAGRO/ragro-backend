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
 * Dono do cache de recomendações por cliente. O rerank LLM roda AQUI, assíncrono — a request
 * responde imediatamente com a ordenação heurística e o ranking da LLM aquece o cache para as
 * próximas. Com isso a LLM é chamada no máximo 1× por cliente por janela de TTL (ou após novo
 * pedido), em vez de 1× por abertura de tela.
 */
@Service
@RequiredArgsConstructor
public class RecommendationWarmupService {

  private static final Logger log = LoggerFactory.getLogger(RecommendationWarmupService.class);

  private final LlmRerankerPort llmRerankerPort;
  private final CacheManager cacheManager;
  private final MeterRegistry meterRegistry;

  /** Evita warm-ups duplicados do mesmo cliente enquanto um já está em andamento. */
  private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

  /** Ranking cacheado do cliente, ou {@code null} em cache frio/expirado. */
  @SuppressWarnings("unchecked")
  public List<RankedRecommendation> getCached(UUID customerId) {
    Cache cache = cacheManager.getCache(CacheConfig.RECOMMENDATIONS_CACHE);
    if (cache == null) {
      return null;
    }
    return cache.get(customerId, List.class);
  }

  /**
   * Reordena os candidatos com a LLM fora da thread da request e grava o resultado no cache. Em
   * falha/saída inválida, cacheia o ranking heurístico (determinístico) — o cliente nunca fica sem
   * recomendações e a LLM não é re-tentada a cada abertura de tela.
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

  /**
   * O perfil do cliente (histórico, categorias preferidas) só muda quando um pedido dele muda de
   * estado — gancho natural de invalidação, em vez de esperar o TTL.
   */
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
