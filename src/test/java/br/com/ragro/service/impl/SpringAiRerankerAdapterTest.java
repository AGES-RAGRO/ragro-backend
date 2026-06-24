package br.com.ragro.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.domain.llm.Candidate;
import br.com.ragro.domain.llm.CustomerFeatures;
import br.com.ragro.domain.llm.RankedItem;
import br.com.ragro.exception.LlmInvalidOutputException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiRerankerAdapterTest {

  private final ChatModel chatModel = mock(ChatModel.class);
  private final SpringAiRerankerAdapter adapter =
      new SpringAiRerankerAdapter(chatModel, new ObjectMapper());

  private Candidate candidate(UUID id, double heuristic) {
    Candidate c = new Candidate();
    c.setProductId(id);
    c.setProductName("Tomate Orgânico");
    c.setProducerName("Sítio Boa Vista");
    c.setCategoryNames(List.of("Hortaliças"));
    c.setUnitPrice(new BigDecimal("9.90"));
    c.setHeuristicScore(heuristic);
    return c;
  }

  private void stubLlmJson(String json) {
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
  }

  private String capturedPromptText() {
    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel).call(captor.capture());
    return captor.getValue().getInstructions().toString();
  }

  @Test
  void rerank_emptyCandidates_returnsEmptyWithoutCallingLlm() {
    assertThat(adapter.rerank(List.of(), new CustomerFeatures())).isEmpty();
  }

  @Test
  void parse_validOutput_mapsAndClampsScores() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    List<Candidate> candidates = List.of(candidate(id1, 3), candidate(id2, 1));

    RerankOutput output =
        new RerankOutput(
            List.of(
                new RerankOutput.RerankEntry(id1.toString(), 1.5, "muito relevante"), // clamp →1.0
                new RerankOutput.RerankEntry(id2.toString(), 0.4, "relevante")));

    List<RankedItem> result = adapter.parse(output, candidates);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getProductId()).isEqualTo(id1);
    assertThat(result.get(0).getScore()).isEqualTo(1.0);
    assertThat(result.get(0).getReason()).isEqualTo("muito relevante");
    // The originating candidate is carried through so callers can hydrate the response.
    assertThat(result.get(0).getCandidate().getProductId()).isEqualTo(id1);
    assertThat(result.get(1).getScore()).isEqualTo(0.4);
  }

  @Test
  void parse_skipsUnknownAndInvalidIds() {
    UUID known = UUID.randomUUID();
    List<Candidate> candidates = List.of(candidate(known, 2));

    RerankOutput output =
        new RerankOutput(
            List.of(
                new RerankOutput.RerankEntry("not-a-uuid", 0.9, "x"),
                new RerankOutput.RerankEntry(UUID.randomUUID().toString(), 0.8, "desconhecido"),
                new RerankOutput.RerankEntry(known.toString(), 0.7, "ok")));

    List<RankedItem> result = adapter.parse(output, candidates);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getProductId()).isEqualTo(known);
  }

  @Test
  void parse_emptyOrNull_throws() {
    List<Candidate> candidates = List.of(candidate(UUID.randomUUID(), 1));

    assertThatThrownBy(() -> adapter.parse(null, candidates))
        .isInstanceOf(LlmInvalidOutputException.class);
    assertThatThrownBy(() -> adapter.parse(new RerankOutput(List.of()), candidates))
        .isInstanceOf(LlmInvalidOutputException.class);
  }

  @Test
  void parse_allEntriesInvalid_throws() {
    List<Candidate> candidates = List.of(candidate(UUID.randomUUID(), 1));
    RerankOutput output =
        new RerankOutput(List.of(new RerankOutput.RerankEntry("bad", 0.5, "x")));

    assertThatThrownBy(() -> adapter.parse(output, candidates))
        .isInstanceOf(LlmInvalidOutputException.class);
  }

  // ─── rerank end-to-end (buildPrompt + ChatClient + parse) ──────────────────

  @Test
  void rerank_happyPath_callsLlmAndReturnsRankedItems() {
    UUID id = UUID.randomUUID();
    List<Candidate> candidates = List.of(candidate(id, 2));
    stubLlmJson("{\"ranked\":[{\"productId\":\"" + id + "\",\"score\":0.85,\"reason\":\"ok\"}]}");

    List<RankedItem> result = adapter.rerank(candidates, new CustomerFeatures());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getProductId()).isEqualTo(id);
    assertThat(result.get(0).getScore()).isEqualTo(0.85);
  }

  @Test
  void rerank_whenLlmCallThrows_isWrappedAsLlmInvalidOutput() {
    List<Candidate> candidates = List.of(candidate(UUID.randomUUID(), 1));
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("connection reset"));

    assertThatThrownBy(() -> adapter.rerank(candidates, new CustomerFeatures()))
        .isInstanceOf(LlmInvalidOutputException.class)
        .hasMessageContaining("LLM rerank call failed");
  }

  @Test
  void rerank_withPreferredCategories_putsThemInThePrompt() {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));
    CustomerFeatures features = new CustomerFeatures();
    features.setPreferredCategories(List.of("Hortaliças"));

    assertThatThrownBy(() -> adapter.rerank(List.of(candidate(UUID.randomUUID(), 1)), features))
        .isInstanceOf(LlmInvalidOutputException.class);

    String prompt = capturedPromptText();
    assertThat(prompt).contains("Preferred categories").contains("Hortaliças");
    // Non-null candidate fields are serialized verbatim into the JSON payload.
    assertThat(prompt).contains("\"producerName\":\"Sítio Boa Vista\"");
    assertThat(prompt).contains("\"categories\":[\"Hortaliças\"]");
  }

  @Test
  void rerank_withFavoriteProducers_putsThemInThePrompt() {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));
    CustomerFeatures features = new CustomerFeatures();
    features.setFavoriteProducers(List.of("Sítio Boa Vista"));

    assertThatThrownBy(() -> adapter.rerank(List.of(candidate(UUID.randomUUID(), 1)), features))
        .isInstanceOf(LlmInvalidOutputException.class);

    String prompt = capturedPromptText();
    assertThat(prompt).contains("Favorite producers").contains("Sítio Boa Vista");
    assertThat(prompt).doesNotContain("Preferred categories");
  }

  @Test
  void rerank_toleratesNullableCandidateFields() {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));
    Candidate c = new Candidate();
    c.setProductId(UUID.randomUUID());
    c.setProductName("Produto");
    c.setProducerName(null);
    c.setCategoryNames(null);
    c.setUnitPrice(null);
    c.setHeuristicScore(1.0);

    assertThatThrownBy(() -> adapter.rerank(List.of(c), new CustomerFeatures()))
        .isInstanceOf(LlmInvalidOutputException.class);

    // null unitPrice defaults to "0", null producer to "" and null categories to [] in the payload.
    String prompt = capturedPromptText();
    assertThat(prompt).contains("\"unitPrice\":\"0\"");
    assertThat(prompt).contains("\"producerName\":\"\"");
    assertThat(prompt).contains("\"categories\":[]");
  }

  @Test
  void rerank_capsCandidatesSentToLlmAtFifty() {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));
    List<Candidate> many =
        IntStream.range(0, 60).mapToObj(i -> candidate(UUID.randomUUID(), i)).toList();

    assertThatThrownBy(() -> adapter.rerank(many, new CustomerFeatures()))
        .isInstanceOf(LlmInvalidOutputException.class);

    // The "Re-rank ALL N" rule reflects the capped count, not the full 60.
    assertThat(capturedPromptText()).contains("Re-rank ALL 50 ");
  }
}
