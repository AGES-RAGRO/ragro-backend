package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.repository.OrderItemRepository;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.ProductRepository;
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

  @InjectMocks private RecommendationService recommendationService;

  // ─── Cenário 1: Happy path ───────────────────────────────────────────────────
  @Test
  void recommend_happyPath_returnsTopScoredProducts() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    UUID purchasedId = UUID.randomUUID();
    Product historyProduct = buildProduct(farmerId);   // sinal 1 → score 3
    Product trendingProduct = buildProduct(farmerId);  // sinal 4 → score 1

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of(purchasedId));
    stubFarmerIds(customer.getId(), List.of(farmerId));
    when(productRepository.findAllByFarmerIdAndActiveTrue(farmerId))
        .thenReturn(List.of(historyProduct));
    stubCoOccurrence(customer.getId(), List.of(purchasedId), List.of());
    // Sinal 3: produtos comprados sem categorias → findAllById retorna produto sem cats
    when(productRepository.findAllById(List.of(purchasedId)))
        .thenReturn(List.of(buildProduct(farmerId))); // produto sem categorias
    stubTrending(List.of(trendingProduct));
    stubRecentProducts(List.of());

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getTotal()).isEqualTo(2);
    List<RecommendationProductResponse> recs = response.getRecommendations();
    assertThat(recs.get(0).getScore()).isGreaterThanOrEqualTo(recs.get(1).getScore());
    assertThat(recs.get(0).getReason()).isEqualTo(RecommendationReason.PURCHASE_HISTORY);
  }

  // ─── Cenário 2: Cliente novo — fallback TRENDING + FRESHNESS ────────────────
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

  // ─── Cenário 3: Limite é aplicado corretamente ──────────────────────────────
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

  // ─── Cenário 4: Produtos já comprados não aparecem ──────────────────────────
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
    when(productRepository.findAllById(List.of(purchasedId)))
        .thenReturn(List.of(alreadyBought));
    stubTrending(List.of());
    stubRecentProducts(List.of());

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).isEmpty();
    assertThat(response.getTotal()).isZero();
  }

  // ─── Cenário 5: excludeProductIds são removidos ─────────────────────────────
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

  // ─── Cenário 6: Score acumula quando produto aparece em múltiplos sinais ────
  @Test
  void recommend_productAppearsInMultipleSignals_scoreAccumulates() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    UUID purchasedId = UUID.randomUUID();
    // Produto aparece em PURCHASE_HISTORY (3) + TRENDING (1) → score = 4
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

  // ─── Cenário 7: Reason é o sinal de maior peso ──────────────────────────────
  @Test
  void recommend_reasonIsHighestWeightSignal() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    UUID purchasedId = UUID.randomUUID();
    // Produto em TRENDING (1) e PURCHASE_HISTORY (3) → reason = PURCHASE_HISTORY
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

  // ─── Cenário 8: ForbiddenException para FARMER ──────────────────────────────
  @Test
  void recommend_forbiddenForFarmer() {
    User farmer = new User();
    farmer.setId(UUID.randomUUID());
    farmer.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(any(Jwt.class))).thenReturn(farmer);

    assertThatThrownBy(
            () -> recommendationService.getRecommendations(defaultRequest(), jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Recomendações disponíveis apenas para consumidores");
  }

  // ─── Cenário 9: Plataforma vazia — retorna lista vazia sem erro ─────────────
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

  // ─── Cenário 10: Cliente comprou tudo — retorna vazio ───────────────────────
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
    when(productRepository.findAllByFarmerIdAndActiveTrue(farmerId))
        .thenReturn(List.of(p1, p2));
    stubCoOccurrence(customer.getId(), purchasedIds, List.of());
    when(productRepository.findAllById(purchasedIds)).thenReturn(List.of(p1, p2));
    stubTrending(List.of(p1, p2));
    stubRecentProducts(List.of());

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).isEmpty();
  }

  // ─── Cenário 11: Co-ocorrência acumula peso 2 ───────────────────────────────
  @Test
  void recommend_coOccurrenceProductsGetWeight2() {
    User customer = buildCustomer();
    UUID farmerId = UUID.randomUUID();
    UUID purchasedId = UUID.randomUUID();
    Product coProduct = buildProduct(farmerId);

    stubCustomer(customer);
    stubPurchasedIds(customer.getId(), List.of(purchasedId));
    stubFarmerIds(customer.getId(), List.of());
    // Sinal 2: co-ocorrência retorna o coProduct
    stubCoOccurrence(customer.getId(), List.of(purchasedId), List.of(coProduct.getId()));
    // findAllById é chamado com a lista de IDs co-ocorrentes (sinal 2)
    when(productRepository.findAllById(List.of(coProduct.getId())))
        .thenReturn(List.of(coProduct));
    // Sinal 3: findAllById com purchasedIds para extrair categorias
    when(productRepository.findAllById(List.of(purchasedId)))
        .thenReturn(List.of(buildProduct(farmerId))); // sem categorias
    stubTrending(List.of());
    stubRecentProducts(List.of());

    RecommendationResponse response =
        recommendationService.getRecommendations(defaultRequest(), jwt());

    assertThat(response.getRecommendations()).hasSize(1);
    assertThat(response.getRecommendations().get(0).getScore()).isEqualTo(2);
    assertThat(response.getRecommendations().get(0).getReason())
        .isEqualTo(RecommendationReason.CO_OCCURRENCE);
  }

  // ─── Cenário 12: Categoria preferida extraída corretamente do histórico ──────
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
    // findAllById para extrair categorias do histórico
    when(productRepository.findAllById(List.of(purchasedId)))
        .thenReturn(List.of(purchasedProduct));
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

  // ─────────────────────────────────────────────────────────────────────────────
  // Builders e stubs
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
}
