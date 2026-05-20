package br.com.ragro.domain.llm;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Candidate {

  private UUID productId;
  private String productName;
  private int score;
}
