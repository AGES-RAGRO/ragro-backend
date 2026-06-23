package br.com.ragro.controller.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Payload published to {@code /topic/routes/{routeId}} on every accepted ping, to all authorized
 * subscribers. Safe data only — producer position and per-order ETA, no other customers' addresses/names
 * (each app filters by its own orderId).
 */
@Getter
@Builder
public class TrackingBroadcast {

  private BigDecimal latitude;
  private BigDecimal longitude;
  private OffsetDateTime recordedAt;

  /** Producer is off the planned route (>200m from the polyline) — UI can flag it. */
  private boolean deviated;

  private List<StopEta> etas;

  @Getter
  @Builder
  public static class StopEta {
    private UUID orderId;
    /** Visit position of the stop (0-based); ETA in seconds from now. */
    private int sequence;
    private Integer etaSeconds;
    /** How many pending stops exist before this one. */
    private int stopsBefore;
    private String status;
  }
}
