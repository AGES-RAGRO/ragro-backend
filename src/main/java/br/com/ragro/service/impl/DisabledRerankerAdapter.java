package br.com.ragro.service.impl;

import br.com.ragro.domain.llm.Candidate;
import br.com.ragro.domain.llm.CustomerFeatures;
import br.com.ragro.domain.llm.RankedItem;
import br.com.ragro.service.api.LlmRerankerPort;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * No-op reranker, active when {@code ragro.recommendations.rerank.enabled=false}. Returns an empty
 * list so {@code RecommendationService} relies solely on heuristic ordering, and guarantees exactly
 * one {@link LlmRerankerPort} bean always exists.
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
