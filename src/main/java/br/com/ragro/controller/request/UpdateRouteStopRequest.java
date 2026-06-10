package br.com.ragro.controller.request;

import br.com.ragro.domain.enums.RouteStopStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Atualiza o status de uma parada da rota (ARRIVED, DELIVERED ou FAILED)")
public class UpdateRouteStopRequest {

  @NotNull(message = "O status é obrigatório")
  private RouteStopStatus status;
}
