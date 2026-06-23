package br.com.ragro.domain.llm;

import br.com.ragro.domain.enums.RecommendationReason;
import java.util.UUID;

/**
 * Recommendation cache entry. Caches only id+score+reason; price/photo/name are resolved from the
 * database at serve time so they never go stale on the TTL.
 */
public record RankedRecommendation(UUID productId, int score, RecommendationReason reason) {}
