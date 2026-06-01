package br.com.ragro.service.impl;

import br.com.ragro.domain.llm.Candidate;
import br.com.ragro.domain.llm.CustomerFeatures;
import br.com.ragro.domain.llm.RankedItem;
import br.com.ragro.service.api.LlmRerankerPort;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Implementação no-op do reranker, ativada quando {@code
 * ragro.recommendations.rerank.enabled=false}. Retorna lista vazia para que o {@code
 * RecommendationService} use exclusivamente a ordenação heurística. Garante que sempre exista
 * exatamente um bean de {@link LlmRerankerPort}.
 */
@Service
@ConditionalOnProperty(
    prefix = "ragro.recommendations.rerank",
    name = "enabled",
    havingValue = "false")
public class DisabledRerankerAdapter implements LlmRerankerPort {

  @Override
  public List<RankedItem> rerank(List<Candidate> candidates, CustomerFeatures features) {
    return List.of();
  }
}
