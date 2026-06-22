package br.com.ragro.domain.llm;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Perfil desidentificado do cliente enviado à LLM no rerank. Mantém apenas o que o
 * RecommendationService realmente popula; {@code recentPurchases}/{@code averageOrderValue}
 * existiam no prompt mas nunca eram preenchidos (código morto removido na auditoria Fase 0).
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class CustomerFeatures {

  private List<String> preferredCategories = new ArrayList<>();
  private List<String> favoriteProducers = new ArrayList<>();

  public boolean isEmpty() {
    return preferredCategories.isEmpty() && favoriteProducers.isEmpty();
  }
}
