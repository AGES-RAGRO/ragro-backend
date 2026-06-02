package br.com.ragro.service.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Structured output expected from the LLM reranker. Spring AI ({@code .entity(...)}) generates the
 * JSON Schema from this type and deserializes the model response automatically, with no manual
 * JSON/markdown parsing.
 */
public record RerankOutput(
    @JsonPropertyDescription(
            "Produtos reordenados por relevância, do mais para o menos relevante")
        List<RerankEntry> ranked) {

  public record RerankEntry(
      @JsonPropertyDescription("UUID do produto, exatamente como fornecido na entrada")
          String productId,
      @JsonPropertyDescription("Score de relevância entre 0.0 e 1.0") Double score,
      @JsonPropertyDescription("Motivo curto em português (máx. 80 caracteres)") String reason) {}
}
