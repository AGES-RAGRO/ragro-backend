package br.com.ragro.service;

import br.com.ragro.controller.request.RecommendationRequest;
import br.com.ragro.controller.response.RecommendationProductResponse;
import br.com.ragro.controller.response.RecommendationResponse;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.RecommendationReason;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.domain.llm.Candidate;
import br.com.ragro.domain.llm.CustomerFeatures;
import br.com.ragro.domain.llm.RankedItem;
import br.com.ragro.domain.llm.RankedRecommendation;
import br.com.ragro.exception.LlmInvalidOutputException;
import br.com.ragro.mapper.RecommendationMapper;
import br.com.ragro.repository.OrderItemRepository;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.ProductRepository;
import br.com.ragro.service.api.LlmRerankerPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

  private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

  private static final int WEIGHT_PURCHASE_HISTORY = 3;
  private static final int WEIGHT_CO_OCCURRENCE = 2;
  private static final int WEIGHT_CATEGORY_PREFERENCE = 2;
  private static final int WEIGHT_TRENDING = 1;
  private static final int WEIGHT_FRESHNESS = 1;

  private static final int TRENDING_DAYS = 90;
  private static final int FRESHNESS_DAYS = 30;
  private static final int CANDIDATE_PAGE_SIZE = 50;

  private final UserService userService;
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final ProductRepository productRepository;
  private final LlmRerankerPort llmRerankerPort;
  private final MeterRegistry meterRegistry;
  private final MinioStorageService minioStorageService;
  private final RecommendationWarmupService warmupService;

  /**
   * Com cache ligado, a LLM nunca roda na thread da request: cache hit responde em ms; miss
   * responde com a ordenação heurística e dispara o rerank assíncrono ({@link
   * RecommendationWarmupService}). Desligado, volta ao comportamento anterior (rerank síncrono a
   * cada chamada) — rollback instantâneo por configuração.
   */
  @Value("${ragro.recommendations.cache.enabled:true}")
  private boolean cacheEnabled;

  @Transactional(readOnly = true)
  public RecommendationResponse getRecommendations(RecommendationRequest request, Jwt jwt) {
    User user = userService.requireRole(jwt, TypeUser.CUSTOMER, "Recomendações disponíveis apenas para consumidores");

    UUID customerId = user.getId();

    List<UUID> purchasedIds = orderItemRepository.findDistinctProductIdsByCustomerId(customerId);
    Set<UUID> purchasedSet = Set.copyOf(purchasedIds);

    if (cacheEnabled) {
      List<RankedRecommendation> cached = warmupService.getCached(customerId);
      if (cached != null) {
        meterRegistry.counter("ragro.recommendation.cache", "result", "hit").increment();
        List<RecommendationProductResponse> fromCache =
            buildFromCache(
                cached,
                resolveExcludeSet(request, purchasedSet),
                request.getCategory(),
                request.getLimit());
        return RecommendationResponse.builder()
            .recommendations(fromCache)
            .total(fromCache.size())
            .build();
      }
      meterRegistry.counter("ragro.recommendation.cache", "result", "miss").increment();
    }

    Map<UUID, int[]> scoreMap = new HashMap<>();
    Map<UUID, RecommendationReason> reasonMap = new HashMap<>();
    Map<UUID, Product> productCache = new HashMap<>();

    boolean hasHistory = !purchasedIds.isEmpty();

    if (hasHistory) {
      List<UUID> farmerIds = orderRepository.findDistinctFarmerIdsByCustomerId(customerId);
      for (UUID farmerId : farmerIds) {
        List<Product> products = productRepository.findAllByFarmerIdAndActiveTrue(farmerId);
        for (Product p : products) {
          accumulate(
              p,
              WEIGHT_PURCHASE_HISTORY,
              RecommendationReason.PURCHASE_HISTORY,
              scoreMap,
              reasonMap,
              productCache);
        }
      }

      List<UUID> coIds = orderItemRepository.findCoOccurringProductIds(customerId, purchasedIds);
      if (!coIds.isEmpty()) {
        List<Product> coProducts = productRepository.findAllByIdAndFarmerUserActiveTrue(coIds);
        for (Product p : coProducts) {
          accumulate(
              p,
              WEIGHT_CO_OCCURRENCE,
              RecommendationReason.CO_OCCURRENCE,
              scoreMap,
              reasonMap,
              productCache);
        }
      }

      List<Integer> preferredCategoryIds = extractPreferredCategoryIds(purchasedIds);
      if (!preferredCategoryIds.isEmpty()) {
        List<Product> categoryProducts =
            productRepository.findActiveProductsByCategoryIds(preferredCategoryIds);
        for (Product p : categoryProducts) {
          accumulate(
              p,
              WEIGHT_CATEGORY_PREFERENCE,
              RecommendationReason.CATEGORY_PREFERENCE,
              scoreMap,
              reasonMap,
              productCache);
        }
      }
    }

    OffsetDateTime trendingSince = OffsetDateTime.now().minusDays(TRENDING_DAYS);
    List<Product> trending =
        productRepository
            .findTrendingProducts(trendingSince, PageRequest.of(0, CANDIDATE_PAGE_SIZE))
            .getContent();
    for (Product p : trending) {
      accumulate(
          p, WEIGHT_TRENDING, RecommendationReason.TRENDING, scoreMap, reasonMap, productCache);
    }

    List<Product> recent =
        productRepository
            .findRecentActiveProducts(PageRequest.of(0, CANDIDATE_PAGE_SIZE))
            .getContent();
    for (Product p : recent) {
      if (p.getCreatedAt() != null
          && p.getCreatedAt().isAfter(OffsetDateTime.now().minusDays(FRESHNESS_DAYS))) {
        accumulate(
            p, WEIGHT_FRESHNESS, RecommendationReason.FRESHNESS, scoreMap, reasonMap, productCache);
      }
    }

    int limit = request.getLimit();
    List<RecommendationProductResponse> recommendations;

    if (cacheEnabled) {
      // Produtos já comprados saem do ranking cacheável (estável até o próximo pedido, que
      // invalida o cache); os excludeProductIds DA REQUEST são por-chamada e filtram só a
      // resposta — nunca entram no cache.
      purchasedSet.forEach(scoreMap::remove);
      warmupService.warmAsync(
          customerId,
          buildCandidates(scoreMap, productCache, reasonMap),
          buildCustomerFeatures(productCache, reasonMap),
          heuristicRankedSnapshot(scoreMap, reasonMap));

      Map<UUID, int[]> serveMap = new HashMap<>(scoreMap);
      if (request.getExcludeProductIds() != null) {
        request.getExcludeProductIds().forEach(serveMap::remove);
      }
      // Filtro de categoria é por-chamada (como excludeProductIds): filtra só a resposta servida,
      // nunca o snapshot cacheável (o warmAsync acima recebeu o scoreMap sem este filtro).
      if (request.getCategory() != null) {
        serveMap
            .keySet()
            .removeIf(id -> !matchesCategory(productCache.get(id), request.getCategory()));
      }
      recommendations = buildHeuristicRecommendations(serveMap, productCache, reasonMap, limit);
    } else {
      // Comportamento anterior (rollback): rerank LLM síncrono na thread da request.
      Set<UUID> excludeSet = resolveExcludeSet(request, purchasedSet);
      excludeSet.forEach(scoreMap::remove);
      if (request.getCategory() != null) {
        scoreMap
            .keySet()
            .removeIf(id -> !matchesCategory(productCache.get(id), request.getCategory()));
      }
      recommendations = buildRecommendations(scoreMap, productCache, reasonMap, limit, customerId);
    }

    return RecommendationResponse.builder()
        .recommendations(recommendations)
        .total(recommendations.size())
        .build();
  }

  /**
   * Serve a resposta a partir do ranking cacheado: filtra excludes e categoria, re-hidrata do banco
   * (só produtos ativos) e limita. O filtro de categoria é por-chamada — entra aqui, não no cache.
   */
  private List<RecommendationProductResponse> buildFromCache(
      List<RankedRecommendation> cached,
      Set<UUID> excludeSet,
      br.com.ragro.domain.enums.ProductCategory category,
      int limit) {
    List<RankedRecommendation> selected =
        cached.stream().filter(r -> !excludeSet.contains(r.productId())).toList();

    // findAllByIdAndFarmerUserActiveTrue filtra produto ativo E produtor ativo (igual aos demais
    // caminhos de serve) — findAllById sozinho reintroduzia produtos de produtor inativo no cache hit.
    Map<UUID, Product> products =
        productRepository
            .findAllByIdAndFarmerUserActiveTrue(
                selected.stream().map(RankedRecommendation::productId).toList())
            .stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

    return selected.stream()
        .map(
            r -> {
              Product product = products.get(r.productId());
              return product == null || !matchesCategory(product, category)
                  ? null
                  : RecommendationMapper.toResponse(
                      product, r.score(), r.reason(), minioStorageService);
            })
        .filter(Objects::nonNull)
        .limit(limit)
        .collect(Collectors.toList());
  }

  /**
   * Casa o produto com a categoria pedida (case-insensitive): compara o label do enum da request
   * com os nomes das categorias (entidade) do produto. {@code null} = sem filtro.
   */
  private boolean matchesCategory(
      Product product, br.com.ragro.domain.enums.ProductCategory category) {
    if (category == null) {
      return true;
    }
    if (product == null || product.getCategories() == null) {
      return false;
    }
    String requested = category.getLabel().toLowerCase(Locale.ROOT);
    return product.getCategories().stream()
        .map(c -> c.getName().toLowerCase(Locale.ROOT))
        .anyMatch(requested::equals);
  }

  /** Ranking heurístico completo (sem limit) na forma cacheável — fallback do warm-up. */
  private List<RankedRecommendation> heuristicRankedSnapshot(
      Map<UUID, int[]> scoreMap, Map<UUID, RecommendationReason> reasonMap) {
    return scoreMap.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
        .map(e -> new RankedRecommendation(e.getKey(), e.getValue()[0], reasonMap.get(e.getKey())))
        .toList();
  }

  private List<RecommendationProductResponse> buildRecommendations(
      Map<UUID, int[]> scoreMap,
      Map<UUID, Product> productCache,
      Map<UUID, RecommendationReason> reasonMap,
      int limit,
      UUID customerId) {

    List<Candidate> candidates = buildCandidates(scoreMap, productCache, reasonMap);
    CustomerFeatures features = buildCustomerFeatures(productCache, reasonMap);

    try {
      List<RankedItem> llmRanked = llmRerankerPort.rerank(candidates, features);
      if (llmRanked == null || llmRanked.isEmpty()) {
        return heuristicFallback(scoreMap, productCache, reasonMap, limit, "empty");
      }
      List<RecommendationProductResponse> llmResult =
          llmRanked.stream()
              .filter(item -> productCache.containsKey(item.getProductId()))
              .sorted(Comparator.comparingDouble(RankedItem::getScore).reversed())
              .limit(limit)
              .map(
                  item ->
                      RecommendationMapper.toResponse(
                          productCache.get(item.getProductId()),
                          (int) (item.getScore() * 100),
                          RecommendationReason.LLM_RERANKED,
                          minioStorageService))
              .collect(Collectors.toList());
      if (llmResult.isEmpty()) {
        return heuristicFallback(scoreMap, productCache, reasonMap, limit, "empty_after_filter");
      }
      meterRegistry.counter("ragro.recommendation.rerank", "outcome", "ai").increment();
      return llmResult;
    } catch (LlmInvalidOutputException e) {
      log.warn("LLM returned invalid output for customer {}: {}", customerId, e.getMessage());
      return heuristicFallback(scoreMap, productCache, reasonMap, limit, "invalid_output");
    } catch (Exception e) {
      log.warn("LLM reranking failed, using heuristic fallback: {}", e.getMessage());
      return heuristicFallback(scoreMap, productCache, reasonMap, limit, "error");
    }
  }

  /**
   * Applies heuristic ordering and records the fallback metric, making it observable when the AI did
   * NOT rerank (LLM provider down, invalid output, flag off, etc.).
   */
  private List<RecommendationProductResponse> heuristicFallback(
      Map<UUID, int[]> scoreMap,
      Map<UUID, Product> productCache,
      Map<UUID, RecommendationReason> reasonMap,
      int limit,
      String reason) {
    meterRegistry
        .counter("ragro.recommendation.rerank", "outcome", "fallback", "reason", reason)
        .increment();
    log.info("Recommendation rerank fallback (heuristic ordering). reason={}", reason);
    return buildHeuristicRecommendations(scoreMap, productCache, reasonMap, limit);
  }

  private List<RecommendationProductResponse> buildHeuristicRecommendations(
      Map<UUID, int[]> scoreMap,
      Map<UUID, Product> productCache,
      Map<UUID, RecommendationReason> reasonMap,
      int limit) {

    List<Map.Entry<UUID, int[]>> ranked = new ArrayList<>(scoreMap.entrySet());
    ranked.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));

    return ranked.stream()
        .limit(limit)
        .map(
            entry -> {
              UUID pid = entry.getKey();
              Product product = productCache.get(pid);
              return RecommendationMapper.toResponse(
                  product, scoreMap.get(pid)[0], reasonMap.get(pid), minioStorageService);
            })
        .collect(Collectors.toList());
  }

  private List<Candidate> buildCandidates(
      Map<UUID, int[]> scoreMap,
      Map<UUID, Product> productCache,
      Map<UUID, RecommendationReason> reasonMap) {

    return scoreMap.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
        .map(
            entry -> {
              Product product = productCache.get(entry.getKey());
              if (product == null) return null;

              Candidate candidate = new Candidate();
              candidate.setProductId(product.getId());
              candidate.setProductName(product.getName());
              if (product.getFarmer() != null) {
                candidate.setProducerId(product.getFarmer().getId());
                candidate.setProducerName(product.getFarmer().getFarmName());
              }
              candidate.setCategoryNames(
                  product.getCategories().stream()
                      .map(ProductCategory::getName)
                      .collect(Collectors.toList()));
              candidate.setUnitPrice(product.getPrice());
              candidate.setHeuristicScore(entry.getValue()[0]);
              return candidate;
            })
        .filter(c -> c != null)
        .collect(Collectors.toList());
  }

  private CustomerFeatures buildCustomerFeatures(
      Map<UUID, Product> productCache, Map<UUID, RecommendationReason> reasonMap) {

    CustomerFeatures features = new CustomerFeatures();
    Set<String> categories = new LinkedHashSet<>();
    Set<String> producers = new LinkedHashSet<>();

    for (Map.Entry<UUID, RecommendationReason> entry : reasonMap.entrySet()) {
      Product p = productCache.get(entry.getKey());
      if (p == null) continue;

      p.getCategories().stream().map(ProductCategory::getName).forEach(categories::add);

      if (entry.getValue() == RecommendationReason.PURCHASE_HISTORY
          && p.getFarmer() != null
          && p.getFarmer().getFarmName() != null) {
        producers.add(p.getFarmer().getFarmName());
      }
    }

    features.setPreferredCategories(new ArrayList<>(categories));
    features.setFavoriteProducers(new ArrayList<>(producers));
    return features;
  }

  private void accumulate(
      Product product,
      int weight,
      RecommendationReason reason,
      Map<UUID, int[]> scoreMap,
      Map<UUID, RecommendationReason> reasonMap,
      Map<UUID, Product> productCache) {

    UUID id = product.getId();
    productCache.putIfAbsent(id, product);

    int[] entry = scoreMap.computeIfAbsent(id, k -> new int[] {0, 0});
    entry[0] += weight;

    if (weight > entry[1]) {
      entry[1] = weight;
      reasonMap.put(id, reason);
    }
  }

  private List<Integer> extractPreferredCategoryIds(List<UUID> purchasedProductIds) {
    if (purchasedProductIds.isEmpty()) {
      return Collections.emptyList();
    }
    return productRepository.findAllById(purchasedProductIds).stream()
        .flatMap(p -> p.getCategories().stream())
        .map(ProductCategory::getId)
        .distinct()
        .collect(Collectors.toList());
  }

  private Set<UUID> resolveExcludeSet(RecommendationRequest request, Set<UUID> purchasedSet) {
    Set<UUID> combined = new HashSet<>(purchasedSet);
    if (request.getExcludeProductIds() != null) {
      combined.addAll(request.getExcludeProductIds());
    }
    return combined;
  }
}
