package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.DeliveryRouteStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RouteResponse {

  private UUID id;
  private DeliveryRouteStatus status;
  private BigDecimal originLatitude;
  private BigDecimal originLongitude;
  private BigDecimal totalDistanceKm;
  private Integer totalDurationSeconds;
  /** Baseline do CO2: soma das idas-e-voltas individuais até cada parada (Route Matrix). */
  private BigDecimal baselineDistanceKm;
  private String overviewPolyline;
  private OffsetDateTime createdAt;
  private OffsetDateTime completedAt;
  private List<RouteStopResponse> stops;
}
