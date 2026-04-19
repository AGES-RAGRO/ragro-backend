package br.com.ragro.controller.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCartItemRequest {

  @NotNull(message = "A quantidade é obrigatória")
  @DecimalMin(value = "1", message = "A quantidade deve ser maior ou igual a 1")
  private BigDecimal quantity;
}
