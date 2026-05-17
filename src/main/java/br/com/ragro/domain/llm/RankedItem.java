package br.com.ragro.domain.llm;

import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode(of = "productId")
@ToString(of = "productId")
public class RankedItem {

  private UUID productId;
  private Double score;
  private String reason;
  private Candidate candidate;

  public void setScore(Double score) {
    if (score == null || score < 0.0 || score > 1.0) {
      throw new IllegalArgumentException("Score must be between 0 and 1, got: " + score);
    }
    this.score = score;
  }
}
