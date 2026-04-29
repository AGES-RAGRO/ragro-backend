package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Payload to record a stock entry for a product")
public class StockEntryRequest {
    
    @NotNull(message = "Product ID is required")
    @Schema(description = "Product identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID productId;
    
     @NotNull(message = "Quantity is required")
  @Positive(message = "Quantity must be greater than zero")
  @Digits(
      integer = 9,
      fraction = 3,
      message = "Quantity must have at most 9 integer digits and 3 decimal places")
  @Schema(description = "Quantity to add to stock", example = "25.500")
  private BigDecimal quantity;

  @Size(max = 500, message = "Notes must contain at most 500 characters")
  @Schema(
      description = "Optional notes about the stock entry",
      example = "Incoming shipment from supplier ABC")
  private String notes;
}
