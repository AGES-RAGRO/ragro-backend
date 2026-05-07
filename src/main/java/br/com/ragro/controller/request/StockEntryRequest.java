package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Payload to register a stock entry")
public class StockEntryRequest {

  @NotNull
  private UUID productId;

  @NotNull
  @DecimalMin("0.001")
  @Digits(integer = 9, fraction = 3)
  private BigDecimal quantity;


  @Size(max = 500)
  private String notes;
}

