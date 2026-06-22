package br.com.ragro.controller.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCartItemRequest {

  // Mesma regra do AddToCartRequest: o domínio aceita quantidades fracionárias (ex.: 0.5 kg).
  // O mínimo era 1, o que impedia decrementar um item adicionado com quantidade fracionária.
  @NotNull(message = "A quantidade é obrigatória")
  @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
  private BigDecimal quantity;
}
