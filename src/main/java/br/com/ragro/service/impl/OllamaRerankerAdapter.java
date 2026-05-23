package br.com.ragro.service.impl;

import br.com.ragro.config.OllamaProperties;
import br.com.ragro.domain.llm.Candidate;
import br.com.ragro.domain.llm.CustomerFeatures;
import br.com.ragro.domain.llm.RankedItem;
import br.com.ragro.exception.LlmInvalidOutputException;
import br.com.ragro.service.api.LlmRerankerPort;
import br.com.ragro.service.impl.ollama.OllamaChatResponse;
import br.com.ragro.service.impl.ollama.OllamaRankedOutput;
import br.com.ragro.service.impl.ollama.OllamaRankedOutput.OllamaRankedEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", properties.getModel());
    requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));
    requestBody.put("format", "json");
    requestBody.put("stream", false);

    OllamaChatResponse response =
        ollamaRestClient
            .post()
            .uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(OllamaChatResponse.class);

    return parseResponse(response, limited);
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

  private List<RankedItem> parseResponse(OllamaChatResponse response, List<Candidate> candidates) {
    if (response == null || response.getMessage() == null) {
      throw new LlmInvalidOutputException("Empty or null response from Ollama");
    }

    String content = response.getMessage().getContent();
    if (content == null || content.isBlank()) {
      throw new LlmInvalidOutputException("Empty content in Ollama response");
    }

    if (content.startsWith("```")) {
      int nl = content.indexOf('\n');
      if (nl != -1) content = content.substring(nl + 1).trim();
      if (content.endsWith("```")) content = content.substring(0, content.lastIndexOf("```")).trim();
    }

    OllamaRankedOutput output;
    try {
      output = objectMapper.readValue(content, OllamaRankedOutput.class);
    } catch (JsonProcessingException e) {
      throw new LlmInvalidOutputException("Failed to parse LLM JSON output: " + e.getMessage(), e);
    }

    if (output.getRanked() == null || output.getRanked().isEmpty()) {
      throw new LlmInvalidOutputException("LLM returned empty ranked list");
    }

    Map<UUID, Candidate> candidateMap =
        candidates.stream().collect(Collectors.toMap(Candidate::getProductId, Function.identity()));

    List<RankedItem> result = new ArrayList<>();
    for (OllamaRankedEntry entry : output.getRanked()) {
      if (entry.getProductId() == null) {
        throw new LlmInvalidOutputException("Missing productId in ranked entry");
      }

      UUID productId;
      try {
        productId = UUID.fromString(entry.getProductId());
      } catch (IllegalArgumentException e) {
        throw new LlmInvalidOutputException(
            "Invalid UUID in LLM output: " + entry.getProductId(), e);
      }

      Candidate candidate = candidateMap.get(productId);
      if (candidate == null) {
        log.warn("LLM returned unknown productId {}, skipping", productId);
        continue;
      }

      double raw = entry.getScore() != null ? entry.getScore() : 0.0;
      double clamped = Math.max(0.0, Math.min(1.0, raw));
      RankedItem rankedItem = new RankedItem();
      rankedItem.setProductId(productId);
      rankedItem.setScore(clamped);
      rankedItem.setReason(entry.getReason() != null ? entry.getReason() : "");
      rankedItem.setCandidate(candidate);
      result.add(rankedItem);
    }

    if (result.isEmpty()) {
      throw new LlmInvalidOutputException("No valid ranked items after validation");
    }

    return result;
  }
}
