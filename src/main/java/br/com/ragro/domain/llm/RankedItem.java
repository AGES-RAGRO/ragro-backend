package br.com.ragro.domain.llm;

import br.com.ragro.domain.enums.RecommendationReason;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RankedItem {

  private UUID productId;
  private int score;
  private RecommendationReason reason;
}
