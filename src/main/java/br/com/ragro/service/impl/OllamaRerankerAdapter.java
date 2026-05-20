package br.com.ragro.service.impl;

import br.com.ragro.config.OllamaProperties;
import br.com.ragro.domain.llm.Candidate;
import br.com.ragro.domain.llm.CustomerFeatures;
import br.com.ragro.domain.llm.RankedItem;
import br.com.ragro.exception.LlmInvalidOutputException;
import br.com.ragro.service.api.LlmRerankerPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OllamaRerankerAdapter implements LlmRerankerPort {

  private static final Logger log = LoggerFactory.getLogger(OllamaRerankerAdapter.class);

  // Payload sent to Ollama contains only product/producer names, categories, prices and
  // heuristic scores — no phone, CPF, address, or payment data.
  private static final int MAX_CANDIDATES_TO_LLM = 50;

  private final RestClient ollamaRestClient;
  private final OllamaProperties properties;
  private final ObjectMapper objectMapper;

  public OllamaRerankerAdapter(
      RestClient ollamaRestClient, OllamaProperties properties, ObjectMapper objectMapper) {
    this.ollamaRestClient = ollamaRestClient;
    this.properties = properties;
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
    OllamaChatRequest request =
        new OllamaChatRequest(
            properties.getModel(), List.of(new OllamaMessage("user", prompt)), "json", false);

    String rawResponse =
        ollamaRestClient
            .post()
            .uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(String.class);

    return parseResponse(rawResponse, limited);
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

    sb.append("\n\nReturn a JSON object with a 'ranked' array containing all ")
        .append(candidates.size())
        .append(" products re-ordered by relevance:\n")
        .append(
            "{\"ranked\":[{\"productId\":\"uuid\",\"score\":0.95,"
                + "\"reason\":\"short reason in Portuguese\"}]}\n\n")
        .append("Rules:\n")
        .append("- Include ALL ")
        .append(candidates.size())
        .append(" products from the input\n")
        .append("- score must be a decimal between 0.0 and 1.0\n")
        .append("- reason must be a short phrase in Portuguese (max 80 characters)\n")
        .append("- Do NOT invent products; only re-rank the provided candidates\n")
        .append("- productId values must match exactly the provided UUIDs");

    return sb.toString();
  }

  private List<RankedItem> parseResponse(String rawResponse, List<Candidate> candidates) {
    try {
      JsonNode root = objectMapper.readTree(rawResponse);

      JsonNode contentNode = root.path("message").path("content");
      if (contentNode.isMissingNode()) {
        throw new LlmInvalidOutputException("Missing 'message.content' in Ollama response");
      }

      JsonNode contentJson = objectMapper.readTree(contentNode.asText());
      JsonNode rankedNode = contentJson.path("ranked");
      if (rankedNode.isMissingNode() || !rankedNode.isArray()) {
        throw new LlmInvalidOutputException("Missing or invalid 'ranked' array in LLM output");
      }

      Map<UUID, Candidate> candidateMap =
          candidates.stream()
              .collect(Collectors.toMap(Candidate::getProductId, Function.identity()));

      List<RankedItem> result = new ArrayList<>();
      for (JsonNode item : rankedNode) {
        String productIdStr = item.path("productId").asText(null);
        if (productIdStr == null) {
          throw new LlmInvalidOutputException("Missing 'productId' in ranked item");
        }

        UUID productId;
        try {
          productId = UUID.fromString(productIdStr);
        } catch (IllegalArgumentException e) {
          throw new LlmInvalidOutputException("Invalid UUID in LLM output: " + productIdStr, e);
        }

        Candidate candidate = candidateMap.get(productId);
        if (candidate == null) {
          log.warn("LLM returned unknown productId {}, skipping", productId);
          continue;
        }

        double scoreValue = item.path("score").asDouble(-1);
        if (scoreValue < 0 || scoreValue > 1) {
          throw new LlmInvalidOutputException("Score out of range [0,1]: " + scoreValue);
        }

        String reason = item.path("reason").asText("");

        RankedItem rankedItem = new RankedItem();
        rankedItem.setProductId(productId);
        rankedItem.setScore(scoreValue);
        rankedItem.setReason(reason);
        rankedItem.setCandidate(candidate);
        result.add(rankedItem);
      }

      if (result.isEmpty()) {
        throw new LlmInvalidOutputException("LLM returned empty ranked list");
      }

      return result;

    } catch (LlmInvalidOutputException e) {
      throw e;
    } catch (Exception e) {
      throw new LlmInvalidOutputException("Failed to parse LLM response: " + e.getMessage(), e);
    }
  }

  // ── Ollama API DTOs ───────────────────────────────────────────────────────

  @Getter
  private static class OllamaChatRequest {
    private final String model;
    private final List<OllamaMessage> messages;
    private final String format;
    private final boolean stream;

    OllamaChatRequest(String model, List<OllamaMessage> messages, String format, boolean stream) {
      this.model = model;
      this.messages = messages;
      this.format = format;
      this.stream = stream;
    }
  }

  @Getter
  private static class OllamaMessage {
    private final String role;
    private final String content;

    OllamaMessage(String role, String content) {
      this.role = role;
      this.content = content;
    }
  }
}
