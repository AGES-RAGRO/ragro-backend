package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.RecommendationRequest;
import br.com.ragro.controller.response.RecommendationProductResponse;
import br.com.ragro.controller.response.RecommendationResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.RecommendationReason;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.domain.llm.RankedItem;
import br.com.ragro.domain.llm.RankedRecommendation;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.LlmInvalidOutputException;
import br.com.ragro.repository.OrderItemRepository;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.ProductRepository;
import br.com.ragro.service.api.LlmRerankerPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

  @Mock private UserService userService;
  @Mock private OrderRepository orderRepository;
  @Mock private OrderItemRepository orderItemRepository;
  @Mock private ProductRepository productRepository;
  @Mock private LlmRerankerPort llmRerankerPort;
  @Mock private MinioStorageService minioStorageService;
  @Mock private RecommendationWarmupService warmupService;

  @Spy private MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @InjectMocks private RecommendationService recommendationService;

  // ─── Scenario 1: Happy path ───────────────────────────────────────────────────
  @Test
  void recommend_happyPath_returnsTopScoredProducts() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    UUID purchasedId = UUID.randomUUID();
    Product historyProduct = buildProduct(farmerId); // signal 1 → score 3
    Product trendingProduct = buildProduct(farmerId); // signal 4 → score 1

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of(purchasedId));
    stubFarmerIds(customer.getId(), List.of(farmerId));
    when(productRepository.findAllByFarmerIdAndActiveTrue(farmerId))
        .thenReturn(List.of(historyProduct));
    stubCoOccurrence(customer.getId(), List.of(purchasedId), List.of());
    // Signal 3: purchased products without categories → findAllById returns one with no categories
    when(productRepository.findAllById(List.of(purchasedId)))
        .thenReturn(List.of(buildProduct(farmerId)));
    stubTrending(List.of(trendingProduct));
    stubRecentProducts(List.of());

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getTotal()).isEqualTo(2);
    List<RecommendationProductResponse> recs = response.getRecommendations();
    assertThat(recs.get(0).getScore()).isGreaterThanOrEqualTo(recs.get(1).getScore());
    assertThat(recs.get(0).getReason()).isEqualTo(RecommendationReason.PURCHASE_HISTORY);
  }

  // ─── Scenario 2: New customer — fallback to TRENDING + FRESHNESS ────────────────
  @Test
  void recommend_newCustomer_fallbackToTrendingAndFreshness() {
    User customer = buildCustomer();
    Product trendingProduct = buildProduct(UUID.randomUUID());
    Product freshProduct = buildRecentProduct(UUID.randomUUID());

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of());
    stubTrending(List.of(trendingProduct));
    stubRecentProducts(List.of(freshProduct));

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    verify(orderRepository, never()).findDistinctFarmerIdsByCustomerId(any());
    verify(orderItemRepository, never()).findCoOccurringProductIds(any(), any());
    verify(productRepository, never()).findActiveProductsByCategoryIds(any());

    assertThat(response.getRecommendations()).isNotEmpty();
    assertThat(response.getRecommendations())
        .extracting(RecommendationProductResponse::getReason)
        .containsAnyOf(RecommendationReason.TRENDING, RecommendationReason.FRESHNESS);
  }

  // ─── Scenario 3: Limit is applied correctly ──────────────────────────────
  @Test
  void recommend_limitIsApplied() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    UUID purchasedId = UUID.randomUUID();
    List<Product> manyProducts = buildProducts(farmerId, 10);

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of(purchasedId));
    stubFarmerIds(customer.getId(), List.of(farmerId));
    when(productRepository.findAllByFarmerIdAndActiveTrue(farmerId)).thenReturn(manyProducts);
    stubCoOccurrence(customer.getId(), List.of(purchasedId), List.of());
    when(productRepository.findAllById(List.of(purchasedId)))
        .thenReturn(List.of(buildProduct(farmerId)));
    stubTrending(List.of());
    stubRecentProducts(List.of());

    RecommendationRequest req = defaultRequest();
    req.setLimit(3);

    RecommendationResponse response = recommendationService.getRecommendations(req, jwt());

    assertThat(response.getRecommendations()).hasSize(3);
    assertThat(response.getTotal()).isEqualTo(3);
  }

  // ─── Scenario 4: Already-purchased products do not appear ──────────────────────────
  @Test
  void recommend_deduplicatesPurchasedProducts() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    Product alreadyBought = buildProduct(farmerId);
    UUID purchasedId = alreadyBought.getId();

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of(purchasedId));
    stubFarmerIds(customer.getId(), List.of(farmerId));
    when(productRepository.findAllByFarmerIdAndActiveTrue(farmerId))
        .thenReturn(List.of(alreadyBought));
    stubCoOccurrence(customer.getId(), List.of(purchasedId), List.of());
    when(productRepository.findAllById(List.of(purchasedId))).thenReturn(List.of(alreadyBought));
    stubTrending(List.of());
    stubRecentProducts(List.of());

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).isEmpty();
    assertThat(response.getTotal()).isZero();
  }

  // ─── Scenario 5: excludeProductIds are removed ─────────────────────────────
  @Test
  void recommend_excludeProductIds_areRemoved() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    Product productToExclude = buildProduct(farmerId);
    Product normalProduct = buildProduct(farmerId);

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of());
    stubTrending(List.of(productToExclude, normalProduct));
    stubRecentProducts(List.of());

    RecommendationRequest req = defaultRequest();
    req.setExcludeProductIds(List.of(productToExclude.getId()));

    RecommendationResponse response = recommendationService.getRecommendations(req, jwt());

    assertThat(response.getRecommendations())
        .extracting(RecommendationProductResponse::getId)
        .doesNotContain(productToExclude.getId())
        .contains(normalProduct.getId());
  }

  // ─── Scenario 6: Score accumulates when a product appears in multiple signals ────
  @Test
  void recommend_productAppearsInMultipleSignals_scoreAccumulates() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    UUID purchasedId = UUID.randomUUID();
    // Product appears in PURCHASE_HISTORY (3) + TRENDING (1) → score = 4
    Product sharedProduct = buildProduct(farmerId);

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of(purchasedId));
    stubFarmerIds(customer.getId(), List.of(farmerId));
    when(productRepository.findAllByFarmerIdAndActiveTrue(farmerId))
        .thenReturn(List.of(sharedProduct));
    stubCoOccurrence(customer.getId(), List.of(purchasedId), List.of());
    when(productRepository.findAllById(List.of(purchasedId)))
        .thenReturn(List.of(buildProduct(farmerId)));
    stubTrending(List.of(sharedProduct));
    stubRecentProducts(List.of());

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).hasSize(1);
    assertThat(response.getRecommendations().get(0).getScore()).isEqualTo(4);
  }

  // ─── Scenario 7: Reason is the highest-weight signal ──────────────────────────────
  @Test
  void recommend_reasonIsHighestWeightSignal() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    UUID purchasedId = UUID.randomUUID();
    // Product in TRENDING (1) and PURCHASE_HISTORY (3) → reason = PURCHASE_HISTORY
    Product p = buildProduct(farmerId);

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of(purchasedId));
    stubFarmerIds(customer.getId(), List.of(farmerId));
    when(productRepository.findAllByFarmerIdAndActiveTrue(farmerId)).thenReturn(List.of(p));
    stubCoOccurrence(customer.getId(), List.of(purchasedId), List.of());
    when(productRepository.findAllById(List.of(purchasedId)))
        .thenReturn(List.of(buildProduct(farmerId)));
    stubTrending(List.of(p));
    stubRecentProducts(List.of());

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations().get(0).getReason())
        .isEqualTo(RecommendationReason.PURCHASE_HISTORY);
  }

  // ─── Scenario 8: ForbiddenException for FARMER ──────────────────────────────
  @Test
  void recommend_forbiddenForFarmer() {
    User farmer = new User();
    farmer.setId(UUID.randomUUID());
    farmer.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(any(Jwt.class))).thenReturn(farmer);

    assertThatThrownBy(() -> recommendationService.getRecommendations(defaultRequest(), jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Recomendações disponíveis apenas para consumidores");
  }

  // ─── Scenario 9: Empty platform — returns empty list without error ─────────────
  @Test
  void recommend_emptyPlatform_returnsEmpty() {
    User customer = buildCustomer();

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of());
    stubTrending(List.of());
    stubRecentProducts(List.of());

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).isEmpty();
    assertThat(response.getTotal()).isZero();
  }

  // ─── Scenario 10: Customer bought everything — returns empty ───────────────────────
  @Test
  void recommend_customerBoughtEverything_returnsEmpty() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    Product p1 = buildProduct(farmerId);
    Product p2 = buildProduct(farmerId);
    List<UUID> purchasedIds = List.of(p1.getId(), p2.getId());

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), purchasedIds);
    stubFarmerIds(customer.getId(), List.of(farmerId));
    when(productRepository.findAllByFarmerIdAndActiveTrue(farmerId)).thenReturn(List.of(p1, p2));
    stubCoOccurrence(customer.getId(), purchasedIds, List.of());
    when(productRepository.findAllById(purchasedIds)).thenReturn(List.of(p1, p2));
    stubTrending(List.of(p1, p2));
    stubRecentProducts(List.of());

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).isEmpty();
  }

  // ─── Scenario 11: Co-occurrence accumulates weight 2 ───────────────────────────────
  @Test
  void recommend_coOccurrenceProductsGetWeight2() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    UUID purchasedId = UUID.randomUUID();
    Product coProduct = buildProduct(farmerId);

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of(purchasedId));
    stubFarmerIds(customer.getId(), List.of());
    // Signal 2: co-occurrence returns coProduct
    stubCoOccurrence(customer.getId(), List.of(purchasedId), List.of(coProduct.getId()));
    // findAllById called with the co-occurring IDs (signal 2)
    when(productRepository.findAllById(List.of(coProduct.getId()))).thenReturn(List.of(coProduct));
    // Signal 3: findAllById with purchasedIds to extract categories (none here)
    when(productRepository.findAllById(List.of(purchasedId)))
        .thenReturn(List.of(buildProduct(farmerId)));
    stubTrending(List.of());
    stubRecentProducts(List.of());

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).hasSize(1);
    assertThat(response.getRecommendations().get(0).getScore()).isEqualTo(2);
    assertThat(response.getRecommendations().get(0).getReason())
        .isEqualTo(RecommendationReason.CO_OCCURRENCE);
  }

  // ─── Scenario 12: Preferred category extracted correctly from history ──────
  @Test
  void recommend_categoryPreference_usesHistoryCategories() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();

    ProductCategory category = new ProductCategory();
    category.setId(42);
    category.setName("Hortaliças");

    Product purchasedProduct = buildProduct(farmerId);
    purchasedProduct.getCategories().add(category);
    UUID purchasedId = purchasedProduct.getId();

    Product categoryProduct = buildProduct(farmerId);

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of(purchasedId));
    stubFarmerIds(customer.getId(), List.of(farmerId));
    when(productRepository.findAllByFarmerIdAndActiveTrue(farmerId)).thenReturn(List.of());
    stubCoOccurrence(customer.getId(), List.of(purchasedId), List.of());
    // findAllById to extract categories from history
    when(productRepository.findAllById(List.of(purchasedId))).thenReturn(List.of(purchasedProduct));
    when(productRepository.findActiveProductsByCategoryIds(List.of(42)))
        .thenReturn(List.of(categoryProduct));
    stubTrending(List.of());
    stubRecentProducts(List.of());

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).hasSize(1);
    assertThat(response.getRecommendations().get(0).getId()).isEqualTo(categoryProduct.getId());
    assertThat(response.getRecommendations().get(0).getReason())
        .isEqualTo(RecommendationReason.CATEGORY_PREFERENCE);
    assertThat(response.getRecommendations().get(0).getScore()).isEqualTo(2);
  }

  // ─── Scenario 13: LLM responds OK → source = LLM_RERANKED ────────────────────
  @Test
  void recommend_llmRespondsOk_sourceIsLlmReranked() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    Product product = buildProduct(farmerId);

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of());
    stubTrending(List.of(product));
    stubRecentProducts(List.of());

    RankedItem rankedItem = new RankedItem();
    rankedItem.setProductId(product.getId());
    rankedItem.setScore(0.9);
    rankedItem.setReason("Produto popular na região");

    when(llmRerankerPort.rerank(any(), any())).thenReturn(List.of(rankedItem));

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).hasSize(1);
    assertThat(response.getRecommendations().get(0).getReason())
        .isEqualTo(RecommendationReason.LLM_RERANKED);
    assertThat(response.getRecommendations().get(0).getScore()).isEqualTo(90);
  }

  // ─── Scenario 14: LLM timeout → heuristic fallback ──────────────────────────
  @Test
  void recommend_llmTimeout_fallbackToHeuristic() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    Product product = buildProduct(farmerId);

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of());
    stubTrending(List.of(product));
    stubRecentProducts(List.of());

    when(llmRerankerPort.rerank(any(), any()))
        .thenThrow(new RuntimeException("Connection timed out"));

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).hasSize(1);
    assertThat(response.getRecommendations().get(0).getReason())
        .isEqualTo(RecommendationReason.TRENDING);
  }

  // ─── Scenario 15: LLM returns malformed JSON → heuristic fallback ──────────
  @Test
  void recommend_llmInvalidJson_fallbackToHeuristic() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    Product product = buildProduct(farmerId);

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of());
    stubTrending(List.of(product));
    stubRecentProducts(List.of());

    when(llmRerankerPort.rerank(any(), any()))
        .thenThrow(new LlmInvalidOutputException("Invalid JSON in LLM response"));

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).hasSize(1);
    assertThat(response.getRecommendations().get(0).getReason())
        .isEqualTo(RecommendationReason.TRENDING);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Builders and stubs
  // ─────────────────────────────────────────────────────────────────────────────

  private User buildCustomer() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setType(TypeUser.CUSTOMER);
    user.setActive(true);
    return user;
  }

  private Product buildProduct(UUID farmerId) {
    Producer farmer = new Producer();
    farmer.setId(farmerId);
    farmer.setFarmName("Sítio Boa Vista");

    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setFarmer(farmer);
    product.setName("Produto Teste");
    product.setPrice(new BigDecimal("10.00"));
    product.setUnityType("kg");
    product.setActive(true);
    product.setCreatedAt(OffsetDateTime.now().minusDays(60));
    return product;
  }

  private Product buildRecentProduct(UUID farmerId) {
    Product p = buildProduct(farmerId);
    p.setCreatedAt(OffsetDateTime.now().minusDays(10));
    return p;
  }

  private List<Product> buildProducts(UUID farmerId, int count) {
    List<Product> products = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      products.add(buildProduct(farmerId));
    }
    return products;
  }

  private void stubCustomer(User user) {
    when(userService.getAuthenticatedUser(any(Jwt.class))).thenReturn(user);
  }

  private void stubPurchasedIds(UUID customerId, List<UUID> ids) {
    when(orderItemRepository.findDistinctProductIdsByCustomerId(customerId)).thenReturn(ids);
  }

  private void stubFarmerIds(UUID customerId, List<UUID> farmerIds) {
    when(orderRepository.findDistinctFarmerIdsByCustomerId(customerId)).thenReturn(farmerIds);
  }

  private void stubCoOccurrence(UUID customerId, List<UUID> purchased, List<UUID> result) {
    when(orderItemRepository.findCoOccurringProductIds(eq(customerId), eq(purchased)))
        .thenReturn(result);
  }

  private void stubTrending(List<Product> products) {
    Page<Product> page = new PageImpl<>(products);
    when(productRepository.findTrendingProducts(any(OffsetDateTime.class), any(Pageable.class)))
        .thenReturn(page);
  }

  private void stubRecentProducts(List<Product> products) {
    Page<Product> page = new PageImpl<>(products);
    when(productRepository.findRecentActiveProducts(any(Pageable.class))).thenReturn(page);
  }

  private RecommendationRequest defaultRequest() {
    RecommendationRequest req = new RecommendationRequest();
    req.setLimit(20);
    return req;
  }

  private Jwt jwt() {
    return new Jwt(
        "token",
        Instant.now(),
        Instant.now().plusSeconds(300),
        Map.of("alg", "none"),
        Map.of("sub", "customer-sub", "email", "customer@example.com"));
  }

  // ─── Cache (E4): hit serve do cache; miss responde heurística e aquece assíncrono ──────────

  @Test
  void getRecommendations_shouldServeFromCache_withoutTouchingLlm_whenCacheHit() {
    org.springframework.test.util.ReflectionTestUtils.setField(
        recommendationService, "cacheEnabled", true);
    User customer = buildCustomer();
    when(userService.getAuthenticatedUser(any())).thenReturn(customer);
    when(orderItemRepository.findDistinctProductIdsByCustomerId(customer.getId()))
        .thenReturn(List.of());

    UUID farmerId = UUID.randomUUID();
    Product cachedProduct = buildProduct(farmerId);
    when(warmupService.getCached(customer.getId()))
        .thenReturn(
            List.of(
                new RankedRecommendation(
                    cachedProduct.getId(), 95, RecommendationReason.LLM_RERANKED)));
    when(productRepository.findAllById(List.of(cachedProduct.getId())))
        .thenReturn(List.of(cachedProduct));

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).hasSize(1);
    assertThat(response.getRecommendations().get(0).getScore()).isEqualTo(95);
    verifyNoInteractions(llmRerankerPort);
    verify(warmupService, never()).warmAsync(any(), any(), any(), any());
  }

  @Test
  void getRecommendations_shouldReturnHeuristicAndWarmAsync_whenCacheMiss() {
    org.springframework.test.util.ReflectionTestUtils.setField(
        recommendationService, "cacheEnabled", true);
    User customer = buildCustomer();
    when(userService.getAuthenticatedUser(any())).thenReturn(customer);
    when(orderItemRepository.findDistinctProductIdsByCustomerId(customer.getId()))
        .thenReturn(List.of());
    when(warmupService.getCached(customer.getId())).thenReturn(null);

    UUID farmerId = UUID.randomUUID();
    Product trendingProduct = buildProduct(farmerId);
    when(productRepository.findTrendingProducts(any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(trendingProduct)));
    when(productRepository.findRecentActiveProducts(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    // Resposta imediata com a ordenação heurística; a LLM roda só no warm-up assíncrono.
    assertThat(response.getRecommendations()).isNotEmpty();
    verifyNoInteractions(llmRerankerPort);
    verify(warmupService).warmAsync(eq(customer.getId()), any(), any(), any());
  }

  @Test
  void getRecommendations_shouldApplyRequestExcludes_onCacheHit() {
    org.springframework.test.util.ReflectionTestUtils.setField(
        recommendationService, "cacheEnabled", true);
    User customer = buildCustomer();
    when(userService.getAuthenticatedUser(any())).thenReturn(customer);
    when(orderItemRepository.findDistinctProductIdsByCustomerId(customer.getId()))
        .thenReturn(List.of());

    UUID farmerId = UUID.randomUUID();
    Product keep = buildProduct(farmerId);
    Product excluded = buildProduct(farmerId);
    when(warmupService.getCached(customer.getId()))
        .thenReturn(
            List.of(
                new RankedRecommendation(excluded.getId(), 99, RecommendationReason.LLM_RERANKED),
                new RankedRecommendation(keep.getId(), 80, RecommendationReason.LLM_RERANKED)));
    when(productRepository.findAllById(List.of(keep.getId()))).thenReturn(List.of(keep));

    RecommendationRequest request = defaultRequest();
    request.setExcludeProductIds(List.of(excluded.getId()));

    RecommendationResponse response = recommendationService.getRecommendations(request, jwt());

    assertThat(response.getRecommendations()).hasSize(1);
    assertThat(response.getRecommendations().get(0).getId()).isEqualTo(keep.getId());
  }
}
