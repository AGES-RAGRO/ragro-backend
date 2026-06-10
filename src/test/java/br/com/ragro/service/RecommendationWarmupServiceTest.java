package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.ragro.config.CacheConfig;
import br.com.ragro.domain.Customer;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.RecommendationReason;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.domain.event.OrderStatusChangedEvent;
import br.com.ragro.domain.llm.Candidate;
import br.com.ragro.domain.llm.CustomerFeatures;
import br.com.ragro.domain.llm.RankedItem;
import br.com.ragro.domain.llm.RankedRecommendation;
import br.com.ragro.service.api.LlmRerankerPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

@ExtendWith(MockitoExtension.class)
class RecommendationWarmupServiceTest {

  @Mock private LlmRerankerPort llmRerankerPort;

  private CacheManager cacheManager;
  private RecommendationWarmupService warmupService;

  private final UUID customerId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    cacheManager = new ConcurrentMapCacheManager(CacheConfig.RECOMMENDATIONS_CACHE);
    warmupService =
        new RecommendationWarmupService(llmRerankerPort, cacheManager, new SimpleMeterRegistry());
  }

  private Candidate candidate() {
    Candidate candidate = new Candidate();
    candidate.setProductId(productId);
    candidate.setProductName("Tomate");
    candidate.setHeuristicScore(3);
    return candidate;
  }

  private List<RankedRecommendation> heuristic() {
    return List.of(
        new RankedRecommendation(productId, 3, RecommendationReason.PURCHASE_HISTORY));
  }

  @Test
  void warmAsync_shouldCacheLlmRanking_whenRerankSucceeds() {
    RankedItem item = new RankedItem();
    item.setProductId(productId);
    item.setScore(0.9);
    when(llmRerankerPort.rerank(any(), any())).thenReturn(List.of(item));

    warmupService.warmAsync(customerId, List.of(candidate()), new CustomerFeatures(), heuristic());

    List<RankedRecommendation> cached = warmupService.getCached(customerId);
    assertThat(cached).hasSize(1);
    assertThat(cached.get(0).score()).isEqualTo(90);
    assertThat(cached.get(0).reason()).isEqualTo(RecommendationReason.LLM_RERANKED);
  }

  @Test
  void warmAsync_shouldCacheHeuristicFallback_whenRerankFails() {
    when(llmRerankerPort.rerank(any(), any())).thenThrow(new RuntimeException("LLM down"));

    warmupService.warmAsync(customerId, List.of(candidate()), new CustomerFeatures(), heuristic());

    List<RankedRecommendation> cached = warmupService.getCached(customerId);
    assertThat(cached).hasSize(1);
    assertThat(cached.get(0).reason()).isEqualTo(RecommendationReason.PURCHASE_HISTORY);
  }

  @Test
  void warmAsync_shouldDropUnknownProductIds_fromLlmOutput() {
    RankedItem known = new RankedItem();
    known.setProductId(productId);
    known.setScore(0.8);
    RankedItem unknown = new RankedItem();
    unknown.setProductId(UUID.randomUUID());
    unknown.setScore(1.0);
    when(llmRerankerPort.rerank(any(), any())).thenReturn(List.of(unknown, known));

    warmupService.warmAsync(customerId, List.of(candidate()), new CustomerFeatures(), heuristic());

    List<RankedRecommendation> cached = warmupService.getCached(customerId);
    assertThat(cached).hasSize(1);
    assertThat(cached.get(0).productId()).isEqualTo(productId);
  }

  @Test
  void evictOnOrderChange_shouldInvalidateCustomerCache() {
    cacheManager
        .getCache(CacheConfig.RECOMMENDATIONS_CACHE)
        .put(customerId, heuristic());

    Customer customer = new Customer();
    customer.setId(customerId);
    Order order = new Order();
    order.setCustomer(customer);

    warmupService.evictOnOrderChange(
        new OrderStatusChangedEvent(order, null, OrderStatus.PENDING, TypeUser.CUSTOMER));

    assertThat(warmupService.getCached(customerId)).isNull();
  }
}
