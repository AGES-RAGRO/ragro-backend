package br.com.ragro.controller.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCartItemRequest {

  // Same rule as AddToCartRequest: fractional quantities allowed (e.g. 0.5 kg).
  @NotNull(message = "A quantidade é obrigatória")
  @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
  private BigDecimal quantity;
}
