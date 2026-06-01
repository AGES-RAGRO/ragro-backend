package br.com.ragro.service.impl;

import br.com.ragro.domain.llm.Candidate;
import br.com.ragro.domain.llm.CustomerFeatures;
import br.com.ragro.domain.llm.RankedItem;
import br.com.ragro.exception.LlmInvalidOutputException;
import br.com.ragro.service.api.LlmRerankerPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Reranker de recomendações baseado em LLM, implementado sobre o Spring AI ({@link ChatClient} +
 * saída estruturada). Substitui a integração artesanal com {@code RestClient}: o provider (Ollama)
 * é auto-configurado via {@code spring.ai.ollama.*} e o JSON é mapeado por {@code .entity(...)}.
 *
 * <p>Ativável por {@code ragro.recommendations.rerank.enabled} (default {@code true}); quando
 * desativado, o {@link DisabledRerankerAdapter} assume e o {@code RecommendationService} usa a
 * ordenação heurística.
 */
@Service
@ConditionalOnProperty(
    prefix = "ragro.recommendations.rerank",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SpringAiRerankerAdapter implements LlmRerankerPort {

  private static final Logger log = LoggerFactory.getLogger(SpringAiRerankerAdapter.class);

  // Payload enviado ao LLM contém apenas nome/categoria/preço de produto e o score heurístico —
  // sem telefone, CPF, endereço ou dados de pagamento.
  private static final int MAX_CANDIDATES_TO_LLM = 50;

  private final ChatClient chatClient;
  private final ObjectMapper objectMapper;

  public SpringAiRerankerAdapter(ChatModel chatModel, ObjectMapper objectMapper) {
    this.chatClient = ChatClient.create(chatModel);
    this.objectMapper = objectMapper;
  }

  @Override
  public List<RankedItem> rerank(List<Candidate> candidates, CustomerFeatures features) {
    if (candidates.isEmpty()) {
      return List.of();
    }

    List<Candidate> limited =
        candidates.size() > MAX_CANDIDATES_TO_LLM
            ? candidates.subList(0, MAX_CANDIDATES_TO_LLM)
            : candidates;

    String prompt = buildPrompt(limited, features);

    RerankOutput output;
    try {
      output = chatClient.prompt().user(prompt).call().entity(RerankOutput.class);
    } catch (RuntimeException e) {
      // Falha de conexão/timeout/parse do Spring AI: converte em saída inválida para o
      // RecommendationService aplicar o fallback heurístico de forma controlada.
      throw new LlmInvalidOutputException("LLM rerank call failed: " + e.getMessage(), e);
    }

    List<RankedItem> ranked = parse(output, limited);
    log.info("LLM rerank applied to {} candidates ({} ranked)", limited.size(), ranked.size());
    return ranked;
  }

  private String buildPrompt(List<Candidate> candidates, CustomerFeatures features) {
    StringBuilder sb = new StringBuilder();
    sb.append(
            "You are a product recommendation re-ranker for an agricultural marketplace "
                + "connecting urban consumers with local family farmers.\n\n")
        .append(
            "Re-rank the following product candidates based on the customer's preferences. "
                + "Assign a relevance score between 0.0 and 1.0 to each product.\n\n");

    if (!features.isEmpty()) {
      sb.append("Customer profile:\n");
      if (!features.getPreferredCategories().isEmpty()) {
        sb.append("- Preferred categories: ")
            .append(features.getPreferredCategories())
            .append("\n");
      }
      if (!features.getFavoriteProducers().isEmpty()) {
        sb.append("- Favorite producers: ").append(features.getFavoriteProducers()).append("\n");
      }
      if (!features.getRecentPurchases().isEmpty()) {
        sb.append("- Recent purchases: ");
        features
            .getRecentPurchases()
            .forEach(
                p ->
                    sb.append(p.getProductName())
                        .append(" (")
                        .append(p.getProducerName())
                        .append(", ")
                        .append(p.getOrderDate())
                        .append("), "));
        sb.append("\n");
      }
      if (features.getAverageOrderValue() != null) {
        sb.append("- Average order value: R$").append(features.getAverageOrderValue()).append("\n");
      }
      sb.append("\n");
    }

    sb.append("Product candidates (JSON):\n");
    try {
      List<Map<String, Object>> candidatePayload =
          candidates.stream()
              .map(
                  c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("productId", c.getProductId().toString());
                    m.put("productName", c.getProductName());
                    m.put("producerName", c.getProducerName() != null ? c.getProducerName() : "");
                    m.put(
                        "categories",
                        c.getCategoryNames() != null ? c.getCategoryNames() : List.of());
                    m.put(
                        "unitPrice",
                        c.getUnitPrice() != null ? c.getUnitPrice().toPlainString() : "0");
                    m.put("heuristicScore", c.getHeuristicScore());
                    return m;
                  })
              .collect(Collectors.toList());
      sb.append(objectMapper.writeValueAsString(candidatePayload));
    } catch (JsonProcessingException e) {
      throw new LlmInvalidOutputException("Failed to serialize candidates for prompt", e);
    }

    sb.append("\n\nRules:\n")
        .append("- Re-rank ALL ")
        .append(candidates.size())
        .append(" provided products; do NOT invent new ones\n")
        .append("- score must be a decimal between 0.0 and 1.0\n")
        .append("- reason must be a short phrase in Portuguese (max 80 characters)\n")
        .append("- productId values must match exactly the provided UUIDs");

    return sb.toString();
  }

  // Visível no pacote para teste unitário direto da validação/mapeamento.
  List<RankedItem> parse(RerankOutput output, List<Candidate> candidates) {
    if (output == null || output.ranked() == null || output.ranked().isEmpty()) {
      throw new LlmInvalidOutputException("LLM returned empty ranked list");
    }

    Map<UUID, Candidate> candidateMap =
        candidates.stream().collect(Collectors.toMap(Candidate::getProductId, c -> c));

    List<RankedItem> result = new ArrayList<>();
    for (RerankOutput.RerankEntry entry : output.ranked()) {
      if (entry.productId() == null) {
        continue;
      }

      UUID productId;
      try {
        productId = UUID.fromString(entry.productId());
      } catch (IllegalArgumentException e) {
        log.warn("LLM returned invalid UUID {}, skipping", entry.productId());
        continue;
      }

      Candidate candidate = candidateMap.get(productId);
      if (candidate == null) {
        log.warn("LLM returned unknown productId {}, skipping", productId);
        continue;
      }

      double raw = entry.score() != null ? entry.score() : 0.0;
      double clamped = Math.max(0.0, Math.min(1.0, raw));
      RankedItem rankedItem = new RankedItem();
      rankedItem.setProductId(productId);
      rankedItem.setScore(clamped);
      rankedItem.setReason(entry.reason() != null ? entry.reason() : "");
      rankedItem.setCandidate(candidate);
      result.add(rankedItem);
    }

    if (result.isEmpty()) {
      throw new LlmInvalidOutputException("No valid ranked items after validation");
    }

    return result;
  }
}
