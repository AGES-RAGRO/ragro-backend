package br.com.ragro.controller.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Tracking snapshot of ONE order for ITS customer ({@code GET /orders/{id}/tracking}): initial
 * screen state and polling fallback when the WebSocket drops. Privacy-minimal: only the count of stops ahead.
 */
@Getter
@Builder
public class OrderTrackingResponse {

  /** false when the order is not on an active route (no position to share). */
  private boolean available;

  /** Active route id — STOMP channel: /topic/routes/{routeId}. */
  private UUID routeId;

  private BigDecimal producerLatitude;
  private BigDecimal producerLongitude;
  private OffsetDateTime recordedAt;

  /** Destination of this delivery (from the order snapshot). */
  private BigDecimal destinationLatitude;
  private BigDecimal destinationLongitude;

  private Integer etaSeconds;
  private int stopsBefore;

  /** Stop status of this delivery: PENDING | ARRIVED | DELIVERED | FAILED. */
  private String stopStatus;

  /**
   * Encoded polyline (Google), server-clipped to [producer's current position (or origin) → customer's stop].
   * Excludes following stops and return-to-origin (privacy). Only in this snapshot, not in position broadcasts.
   */
  private String overviewPolyline;
}
