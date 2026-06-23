package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Optional body for {@code PATCH /routes/{id}/add-stops}. When the producer's current GPS is sent, the
 * remaining route is re-anchored and re-optimized from where the producer is NOW (they move during a
 * delivery). Both fields are optional: with no coordinates the route keeps its existing origin.
 */
@Getter
@Setter
@Schema(description = "GPS atual do produtor para re-ancorar a rota ao adicionar paradas (opcional)")
public class AddStopsRequest {

  @DecimalMin(value = "-90.0", message = "Latitude deve estar entre -90 e 90")
  @DecimalMax(value = "90.0", message = "Latitude deve estar entre -90 e 90")
  @Schema(description = "Latitude atual do produtor (re-ancora a origem da rota)", example = "-30.0346")
  private BigDecimal originLatitude;

  @DecimalMin(value = "-180.0", message = "Longitude deve estar entre -180 e 180")
  @DecimalMax(value = "180.0", message = "Longitude deve estar entre -180 e 180")
  @Schema(description = "Longitude atual do produtor", example = "-51.2177")
  private BigDecimal originLongitude;

  /** True only when BOTH coordinates are present (a usable origin). */
  public boolean hasOrigin() {
    return originLatitude != null && originLongitude != null;
  }
}
