package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.StockMovementReason;
import br.com.ragro.domain.enums.StockMovementType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockMovementResponse {

  private UUID id;
  private UUID productId;
  private String productName;
  private StockMovementType type;
  private StockMovementReason reason;
  private BigDecimal quantity;
  private String notes;
  private OffsetDateTime createdAt;
}