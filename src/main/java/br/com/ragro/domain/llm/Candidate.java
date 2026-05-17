package br.com.ragro.domain.llm;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode(of = "productId")
@ToString(of = "productId")
public class Candidate {

  private UUID productId;
  private String productName;
  private UUID producerId;
  private String producerName;
  private List<String> categoryNames;
  private BigDecimal unitPrice;
  private double heuristicScore;
}