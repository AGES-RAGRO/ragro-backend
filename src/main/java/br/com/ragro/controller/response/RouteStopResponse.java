package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.RouteStopStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RouteStopResponse {

  private UUID id;
  private UUID orderId;
  /** Position in the optimized route (0-based, visit order). */
  private int sequence;
  private RouteStopStatus status;
  private BigDecimal latitude;
  private BigDecimal longitude;
  private String addressText;
  private String customerName;
  /** Distance/duration of the leg arriving at this stop. */
  private BigDecimal legDistanceKm;
  private Integer legDurationSeconds;
  /** Absolute ETA estimated at route creation. */
  private OffsetDateTime eta;
  private OffsetDateTime completedAt;
}
