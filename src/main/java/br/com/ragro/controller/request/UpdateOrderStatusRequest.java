package br.com.ragro.controller.request;

import br.com.ragro.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOrderStatusRequest {

  @NotNull(message = "O status é obrigatório")
  private OrderStatus status;
}
