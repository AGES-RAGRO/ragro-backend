package br.com.ragro.domain.enums;

public enum RecommendationReason {
  /** Sinal 1 (peso 3): produto do mesmo produtor que o cliente já comprou. */
  PURCHASE_HISTORY,
  /** Sinal 2 (peso 2): produto que co-ocorre nos mesmos pedidos do histórico do cliente. */
  CO_OCCURRENCE,
  /** Sinal 3 (peso 2): produto de categoria preferida do cliente. */
  CATEGORY_PREFERENCE,
  /** Sinal 4 (peso 1): produto mais pedido na plataforma nos últimos 90 dias. */
  TRENDING,
  /** Sinal 5 (peso 1): produto recém-cadastrado (últimos 30 dias). */
  FRESHNESS
}
