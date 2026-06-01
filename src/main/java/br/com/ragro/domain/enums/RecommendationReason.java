package br.com.ragro.domain.enums;

public enum RecommendationReason {
  /** Signal 1 (weight 3): product from a producer the customer has already bought from. */
  PURCHASE_HISTORY,
  /** Signal 2 (weight 2): product that co-occurs in the customer's past orders. */
  CO_OCCURRENCE,
  /** Signal 3 (weight 2): product in a category the customer prefers. */
  CATEGORY_PREFERENCE,
  /** Signal 4 (weight 1): most ordered product on the platform in the last 90 days. */
  TRENDING,
  /** Signal 5 (weight 1): recently added product (last 30 days). */
  FRESHNESS,
  LLM_RERANKED
}
