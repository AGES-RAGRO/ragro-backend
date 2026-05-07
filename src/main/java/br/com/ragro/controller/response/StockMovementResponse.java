package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.StockMovementReason;
import br.com.ragro.domain.enums.StockMovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Stock movement response")
public class StockMovementResponse {

  private UUID id;
  private UUID productId;
  private String productName;
  private StockMovementType type;
  private StockMovementReason reason;
  private BigDecimal quantity;
  private String notes;
  private OffsetDateTime createdAt;
  private BigDecimal currentStockQuantity;
}
