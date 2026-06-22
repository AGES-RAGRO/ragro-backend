package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Cria a rota de entrega a partir dos pedidos CONFIRMED/IN_DELIVERY do produtor")
public class CreateRouteRequest {

  @NotNull(message = "A latitude de origem é obrigatória")
  @DecimalMin(value = "-90.0", message = "Latitude deve estar entre -90 e 90")
  @DecimalMax(value = "90.0", message = "Latitude deve estar entre -90 e 90")
  @Schema(description = "Latitude atual do produtor (origem e retorno da rota)", example = "-30.0346")
  private BigDecimal originLatitude;

  @NotNull(message = "A longitude de origem é obrigatória")
  @DecimalMin(value = "-180.0", message = "Longitude deve estar entre -180 e 180")
  @DecimalMax(value = "180.0", message = "Longitude deve estar entre -180 e 180")
  @Schema(description = "Longitude atual do produtor", example = "-51.2177")
  private BigDecimal originLongitude;
}
