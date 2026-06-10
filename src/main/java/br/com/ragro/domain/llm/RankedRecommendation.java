package br.com.ragro.domain.llm;

import br.com.ragro.domain.enums.RecommendationReason;
import java.util.UUID;

/**
 * Entrada do cache de recomendações: o ranking por cliente é cacheado como ids+score+motivo (não o
 * response montado) — preço/foto/nome do produto são resolvidos do banco na hora de servir, então
 * nunca ficam defasados pelo TTL.
 */
public record RankedRecommendation(UUID productId, int score, RecommendationReason reason) {}
