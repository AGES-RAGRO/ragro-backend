package br.com.ragro.controller.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddToCartRequest {

  @NotNull(message = "O ID do produto é obrigatório")
  private UUID productId;

  @NotNull(message = "A quantidade é obrigatória")
  @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
  private BigDecimal quantity;
}
