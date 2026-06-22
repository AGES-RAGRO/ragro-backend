package br.com.ragro.controller.request;

import br.com.ragro.domain.enums.RouteStopStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Atualiza o status de uma parada da rota (ARRIVED, DELIVERED ou FAILED)")
public class UpdateRouteStopRequest {

  @NotNull(message = "O status é obrigatório")
  private RouteStopStatus status;

  // Código do consumidor — obrigatório quando status=DELIVERED (a obrigatoriedade condicional é
  // validada no serviço); aqui só garante o formato de 4 dígitos quando presente.
  @Pattern(regexp = "\\d{4}", message = "O código de confirmação deve conter 4 dígitos")
  @Schema(
      description = "Código de confirmação do consumidor — obrigatório quando status=DELIVERED",
      example = "1234")
  private String code;
}
