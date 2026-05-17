package br.com.ragro.domain.enums;

public enum RecommendationReason {
  /** Sinal 1 (peso 3): produto do mesmo produtor que o cliente ja comprou. */
  PURCHASE_HISTORY,
  /** Sinal 2 (peso 2): produto que co-ocorre nos mesmos pedidos do historico do cliente. */
  CO_OCCURRENCE,
  /** Sinal 3 (peso 2): produto de categoria preferida do cliente. */
  CATEGORY_PREFERENCE,
  /** Sinal 4 (peso 1): produto mais pedido na plataforma nos ultimos 90 dias. */
  TRENDING,
  /** Sinal 5 (peso 1): produto recem-cadastrado (ultimos 30 dias). */
  FRESHNESS,
  LLM_RERANKED
}
