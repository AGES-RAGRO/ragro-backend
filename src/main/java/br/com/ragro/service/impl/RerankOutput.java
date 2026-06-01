package br.com.ragro.service.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Saída estruturada esperada do LLM reranker. O Spring AI ({@code .entity(...)}) gera o JSON Schema
 * a partir deste tipo e desserializa a resposta do modelo automaticamente — sem parsing manual de
 * JSON/markdown.
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
