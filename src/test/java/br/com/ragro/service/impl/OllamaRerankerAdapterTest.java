package br.com.ragro.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.ragro.config.OllamaProperties;
import br.com.ragro.domain.llm.Candidate;
import br.com.ragro.domain.llm.CustomerFeatures;
import br.com.ragro.domain.llm.RankedItem;
import br.com.ragro.exception.LlmInvalidOutputException;
import br.com.ragro.service.impl.ollama.OllamaChatResponse;
import br.com.ragro.service.impl.ollama.OllamaResponseMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class OllamaRerankerAdapterTest {

  @Mock private RestClient restClient;
  @Mock private OllamaProperties properties;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private OllamaRerankerAdapter adapter;

  @BeforeEach
  void setUp() {
    // lenient: o cenário de candidatos vazios faz short-circuit antes de usar o model.
    lenient().when(properties.getModel()).thenReturn("llama3");
    adapter = new OllamaRerankerAdapter(restClient, properties, objectMapper);
  }

  // ─── Cenário 1: JSON válido → RankedItem correto ─────────────────────────────

  @Test
  void shouldParseValidJsonResponse() {
    // Given
    UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    Candidate candidate = buildCandidate(productId);
    String json =
        "{\"ranked\":[{\"productId\":\""
            + productId
            + "\",\"score\":0.9,\"reason\":\"Produto fresco\"}]}";
    stubRestClient(json);

    // When
    List<RankedItem> result = adapter.rerank(List.of(candidate), emptyFeatures());

    // Then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getProductId()).isEqualTo(productId);
    assertThat(result.get(0).getScore()).isEqualTo(0.9);
    assertThat(result.get(0).getReason()).isEqualTo("Produto fresco");
  }

  // ─── Cenário 2: Fences markdown são removidas antes do parse ─────────────────

  @Test
  void shouldStripMarkdownFences() {
    // Given
    UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    Candidate candidate = buildCandidate(productId);
    String jsonBody =
        "{\"ranked\":[{\"productId\":\""
            + productId
            + "\",\"score\":0.7,\"reason\":\"Org\\u00e2nico\"}]}";
    stubRestClient("```json\n" + jsonBody + "\n```");

    // When
    List<RankedItem> result = adapter.rerank(List.of(candidate), emptyFeatures());

    // Then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getScore()).isEqualTo(0.7);
  }

  // ─── Cenário 3: JSON malformado → LlmInvalidOutputException ──────────────────

  @Test
  void shouldThrowOnMalformedJson() {
    // Given
    Candidate candidate = buildCandidate(UUID.randomUUID());
    stubRestClient("{ not valid json }");

    // When / Then
    assertThatThrownBy(() -> adapter.rerank(List.of(candidate), emptyFeatures()))
        .isInstanceOf(LlmInvalidOutputException.class)
        .hasMessageContaining("Failed to parse LLM JSON output");
  }

  // ─── Cenário 4: Lista ranked vazia → LlmInvalidOutputException ───────────────

  @Test
  void shouldThrowOnEmptyRankedItems() {
    // Given
    Candidate candidate = buildCandidate(UUID.randomUUID());
    stubRestClient("{\"ranked\":[]}");

    // When / Then
    assertThatThrownBy(() -> adapter.rerank(List.of(candidate), emptyFeatures()))
        .isInstanceOf(LlmInvalidOutputException.class)
        .hasMessageContaining("empty ranked list");
  }

  // ─── Cenário 5: UUID desconhecido é ignorado (log WARN), UUID válido permanece ─

  @Test
  void shouldFilterUnknownProductIds() {
    // Given
    UUID knownId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID unknownId = UUID.fromString("00000000-0000-0000-0000-000000000099");
    Candidate candidate = buildCandidate(knownId);
    String json =
        "{\"ranked\":["
            + "{\"productId\":\""
            + knownId
            + "\",\"score\":0.8,\"reason\":\"V\\u00e1lido\"},"
            + "{\"productId\":\""
            + unknownId
            + "\",\"score\":0.5,\"reason\":\"Desconhecido\"}"
            + "]}";
    stubRestClient(json);

    // When
    List<RankedItem> result = adapter.rerank(List.of(candidate), emptyFeatures());

    // Then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getProductId()).isEqualTo(knownId);
  }

  // ─── Cenário 6: Score fora de [0,1] é clampado (1.5 → 1.0, -0.5 → 0.0) ──────

  @Test
  void shouldClampScore() {
    // Given
    UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    Candidate c1 = buildCandidate(id1);
    Candidate c2 = buildCandidate(id2);
    String json =
        "{\"ranked\":["
            + "{\"productId\":\""
            + id1
            + "\",\"score\":1.5,\"reason\":\"Alto\"},"
            + "{\"productId\":\""
            + id2
            + "\",\"score\":-0.5,\"reason\":\"Baixo\"}"
            + "]}";
    stubRestClient(json);

    // When
    List<RankedItem> result = adapter.rerank(List.of(c1, c2), emptyFeatures());

    // Then
    assertThat(result).hasSize(2);
    RankedItem high =
        result.stream().filter(r -> r.getProductId().equals(id1)).findFirst().orElseThrow();
    RankedItem low =
        result.stream().filter(r -> r.getProductId().equals(id2)).findFirst().orElseThrow();
    assertThat(high.getScore()).isEqualTo(1.0);
    assertThat(low.getScore()).isEqualTo(0.0);
  }

  // ─── Cenário 7: reason ausente no JSON → "" (não null) ───────────────────────

  @Test
  void shouldDefaultMissingReason() {
    // Given
    UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    Candidate candidate = buildCandidate(productId);
    String json = "{\"ranked\":[{\"productId\":\"" + productId + "\",\"score\":0.5}]}";
    stubRestClient(json);

    // When
    List<RankedItem> result = adapter.rerank(List.of(candidate), emptyFeatures());

    // Then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getReason()).isNotNull().isEmpty();
  }

  // ─── Cenário 8: resposta sem message → LlmInvalidOutputException ─────────────

  @Test
  void shouldThrowOnNullMessage() {
    // Given
    Candidate candidate = buildCandidate(UUID.randomUUID());
    OllamaChatResponse response = new OllamaChatResponse();
    response.setMessage(null);
    stubRestClient(response);

    // When / Then
    assertThatThrownBy(() -> adapter.rerank(List.of(candidate), emptyFeatures()))
        .isInstanceOf(LlmInvalidOutputException.class)
        .hasMessageContaining("Empty or null response");
  }

  // ─── Cenário 9: conteúdo em branco → LlmInvalidOutputException ───────────────

  @Test
  void shouldThrowOnBlankContent() {
    // Given
    Candidate candidate = buildCandidate(UUID.randomUUID());
    stubRestClient("   ");

    // When / Then
    assertThatThrownBy(() -> adapter.rerank(List.of(candidate), emptyFeatures()))
        .isInstanceOf(LlmInvalidOutputException.class)
        .hasMessageContaining("Empty content");
  }

  // ─── Cenário 10: entry sem productId → LlmInvalidOutputException ─────────────

  @Test
  void shouldThrowOnMissingProductId() {
    // Given
    Candidate candidate = buildCandidate(UUID.randomUUID());
    stubRestClient("{\"ranked\":[{\"score\":0.5,\"reason\":\"sem id\"}]}");

    // When / Then
    assertThatThrownBy(() -> adapter.rerank(List.of(candidate), emptyFeatures()))
        .isInstanceOf(LlmInvalidOutputException.class)
        .hasMessageContaining("Missing productId");
  }

  // ─── Cenário 11: productId que não é UUID → LlmInvalidOutputException ────────

  @Test
  void shouldThrowOnInvalidUuid() {
    // Given
    Candidate candidate = buildCandidate(UUID.randomUUID());
    stubRestClient("{\"ranked\":[{\"productId\":\"not-a-uuid\",\"score\":0.5}]}");

    // When / Then
    assertThatThrownBy(() -> adapter.rerank(List.of(candidate), emptyFeatures()))
        .isInstanceOf(LlmInvalidOutputException.class)
        .hasMessageContaining("Invalid UUID");
  }

  // ─── Cenário 12: todos os productId desconhecidos → nenhum item válido ──────

  @Test
  void shouldThrowWhenAllProductIdsUnknown() {
    // Given
    UUID knownId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID unknownId = UUID.fromString("00000000-0000-0000-0000-000000000099");
    Candidate candidate = buildCandidate(knownId);
    stubRestClient(
        "{\"ranked\":[{\"productId\":\"" + unknownId + "\",\"score\":0.5,\"reason\":\"x\"}]}");

    // When / Then
    assertThatThrownBy(() -> adapter.rerank(List.of(candidate), emptyFeatures()))
        .isInstanceOf(LlmInvalidOutputException.class)
        .hasMessageContaining("No valid ranked items");
  }

  // ─── Cenário 13: lista de candidatos vazia → retorna vazio sem chamar o LLM ─

  @Test
  void shouldReturnEmptyWhenNoCandidates() {
    // When
    List<RankedItem> result = adapter.rerank(List.of(), emptyFeatures());

    // Then
    assertThat(result).isEmpty();
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────────────────────────────────────

  private void stubRestClient(String jsonContent) {
    OllamaChatResponse response = new OllamaChatResponse();
    OllamaResponseMessage message = new OllamaResponseMessage();
    message.setContent(jsonContent);
    response.setMessage(message);
    stubRestClient(response);
  }

  private void stubRestClient(OllamaChatResponse response) {
    // RETURNS_SELF faz cada método fluente (uri, contentType, body) devolver o próprio mock,
    // evitando problemas com a inferência de tipo genérico das interfaces aninhadas do RestClient.
    RestClient.RequestBodyUriSpec uriSpec =
        mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(uriSpec);
    when(uriSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(OllamaChatResponse.class)).thenReturn(response);
  }

  private Candidate buildCandidate(UUID productId) {
    Candidate c = new Candidate();
    c.setProductId(productId);
    c.setProductName("Alface Orgânica");
    c.setProducerName("Sítio Verde");
    c.setCategoryNames(List.of("Hortaliças"));
    c.setUnitPrice(new BigDecimal("5.00"));
    c.setHeuristicScore(0.8);
    return c;
  }

  private CustomerFeatures emptyFeatures() {
    return new CustomerFeatures();
  }
}
