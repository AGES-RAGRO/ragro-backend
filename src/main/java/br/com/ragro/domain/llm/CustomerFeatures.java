package br.com.ragro.domain.llm;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerFeatures {

  private UUID customerId;
  private List<UUID> purchasedProductIds;
  private List<Integer> preferredCategoryIds;
}
