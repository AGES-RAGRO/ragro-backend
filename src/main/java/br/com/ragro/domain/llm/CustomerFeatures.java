package br.com.ragro.domain.llm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode
@ToString
public class CustomerFeatures {

  private List<String> preferredCategories = new ArrayList<>();
  private List<String> favoriteProducers = new ArrayList<>();
  private List<PurchaseSummary> recentPurchases = new ArrayList<>();
  private BigDecimal averageOrderValue;

  public boolean isEmpty() {
    return preferredCategories.isEmpty()
        && favoriteProducers.isEmpty()
        && recentPurchases.isEmpty();
  }

  @Getter
  @Setter
  @EqualsAndHashCode
  @ToString
  public static class PurchaseSummary {

    private String productName;
    private String producerName;
    private BigDecimal quantity;
    private LocalDate orderDate;
  }
}
