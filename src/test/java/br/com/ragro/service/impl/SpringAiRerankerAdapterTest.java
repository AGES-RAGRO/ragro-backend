package br.com.ragro.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import br.com.ragro.domain.llm.Candidate;
import br.com.ragro.domain.llm.CustomerFeatures;
import br.com.ragro.domain.llm.RankedItem;
import br.com.ragro.exception.LlmInvalidOutputException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class SpringAiRerankerAdapterTest {

  private final SpringAiRerankerAdapter adapter =
      new SpringAiRerankerAdapter(mock(ChatModel.class), new ObjectMapper());

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
}
